package com.careerbridge.assessment.service;

import com.careerbridge.assessment.constants.AssessmentConstants;
import com.careerbridge.assessment.constants.AssessmentSection;
import com.careerbridge.assessment.dto.AnswerDto;
import com.careerbridge.assessment.dto.AssessmentRequest;
import com.careerbridge.assessment.dto.AssessmentResponse;
import com.careerbridge.assessment.dto.AssessmentResultDto;
import com.careerbridge.assessment.dto.CategoryDto;
import com.careerbridge.assessment.dto.OptionDto;
import com.careerbridge.assessment.dto.QuestionDto;
import com.careerbridge.assessment.dto.SubmitAnswerRequest;
import com.careerbridge.assessment.event.AssessmentCompletedEvent;
import com.careerbridge.assessment.exception.CustomException;
import com.careerbridge.assessment.model.AssessmentAttempt;
import com.careerbridge.assessment.model.AssessmentResult;
import com.careerbridge.assessment.model.AttemptAnswer;
import com.careerbridge.assessment.model.AttemptStatus;
import com.careerbridge.assessment.model.CareerPath;
import com.careerbridge.assessment.model.Category;
import com.careerbridge.assessment.model.Option;
import com.careerbridge.assessment.model.Question;
import com.careerbridge.assessment.repository.AssessmentAttemptRepository;
import com.careerbridge.assessment.repository.AssessmentResultRepository;
import com.careerbridge.assessment.repository.AttemptAnswerRepository;
import com.careerbridge.assessment.repository.CareerPathRepository;
import com.careerbridge.assessment.repository.CategoryRepository;
import com.careerbridge.assessment.repository.OptionRepository;
import com.careerbridge.assessment.repository.QuestionRepository;
import com.careerbridge.assessment.util.ScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class AssessmentServiceImpl implements AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentServiceImpl.class);

    /**
     * Jackson 3 (tools.jackson) -- the only Jackson on this service's compile classpath.
     * Static rather than injected: this JSON is an internal storage format for one column, not an
     * HTTP payload, so it needs no Spring customisation and stays out of the unit tests' mock setup.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Double>> CAREER_SCORES_TYPE =
            new TypeReference<>() {
            };

    private final CategoryRepository categoryRepository;
    private final QuestionRepository questionRepository;
    private final OptionRepository optionRepository;
    private final CareerPathRepository careerPathRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final AssessmentResultRepository resultRepository;
    private final RabbitTemplate rabbitTemplate;

    public AssessmentServiceImpl(CategoryRepository categoryRepository,
                                 QuestionRepository questionRepository,
                                 OptionRepository optionRepository,
                                 CareerPathRepository careerPathRepository,
                                 AssessmentAttemptRepository attemptRepository,
                                 AttemptAnswerRepository attemptAnswerRepository,
                                 AssessmentResultRepository resultRepository,
                                 RabbitTemplate rabbitTemplate) {
        this.categoryRepository = categoryRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.careerPathRepository = careerPathRepository;
        this.attemptRepository = attemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.resultRepository = resultRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> CategoryDto.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestions(Long categoryId) {
        // 404 rather than an empty list: an unknown category is a client error, not "no questions".
        if (!categoryRepository.existsById(categoryId)) {
            throw new CustomException("Category not found", HttpStatus.NOT_FOUND);
        }
        // Preview returns the whole bank, shuffled. Only startAttempt narrows to the scored subset.
        return toQuestionDtos(shuffled(
                questionRepository.findByCategoryIdAndIsActiveTrueOrderByOrderIndexAsc(categoryId)));
    }

    @Override
    @Transactional
    public AssessmentResponse startAttempt(Long userId, AssessmentRequest request) {
        AssessmentSection section = parseSection(request.getSection());

        // Random pick from the section's pool, every call -- a retake can land on a different
        // underlying category. The client is never told which pool member won; it only ever sees
        // the section's own display name below.
        List<String> pool = AssessmentConstants.SECTION_CATEGORY_POOL.get(section);
        String categoryName = pool.get(new Random().nextInt(pool.size()));
        Category category = categoryRepository.findByName(categoryName)
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));

        // Fail before the student answers anything: a thin category cannot produce a meaningful
        // percentage, and an empty one would divide into a 0% score that looks like a real result.
        // Counts ACTIVE questions only, matching selectQuestionsForAttempt's pool exactly. If this
        // counted retired questions the guard could pass on 6 while the pool served 3, and the fixed
        // maxPossibleScore would cap the student's score with nothing logged.
        int questionCount = safeCount(
                questionRepository.countByCategoryIdAndIsActiveTrue(category.getId()));
        if (questionCount < AssessmentConstants.MIN_QUESTIONS_PER_CATEGORY) {
            throw new CustomException(
                    "Category '" + category.getName() + "' has too few questions to assess",
                    HttpStatus.BAD_REQUEST);
        }

        attemptRepository.findByUserIdAndSectionAndStatus(
                        userId, section.name(), AttemptStatus.IN_PROGRESS)
                .ifPresent(existing -> {
                    throw new CustomException(
                            "An assessment for this section is already in progress",
                            HttpStatus.CONFLICT);
                });

        AssessmentAttempt attempt = attemptRepository.save(AssessmentAttempt.builder()
                .userId(userId)
                .categoryId(category.getId())
                .section(section.name())
                .status(AttemptStatus.IN_PROGRESS)
                .build());

        // Reshuffled per attempt: two students -- or one student retrying -- do not get the same
        // questions in the same order. All active questions in the picked category are served, not
        // a random subset -- category size IS the section's target size now, see data.sql.
        return AssessmentResponse.builder()
                .attemptId(attempt.getId())
                .categoryId(category.getId())
                .categoryName(section.getDisplayName())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .questions(toQuestionDtos(
                        selectQuestionsForAttempt(category.getId(), section.getTargetSize())))
                .build();
    }

    private AssessmentSection parseSection(String raw) {
        try {
            return AssessmentSection.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new CustomException("Unknown assessment section: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional
    public AssessmentResultDto submitAttempt(Long userId, SubmitAnswerRequest request) {
        // Ownership is part of the lookup: another user's attemptId comes back empty and is
        // answered 404, so the endpoint never confirms the row exists.
        AssessmentAttempt attempt = attemptRepository
                .findByIdAndUserId(request.getAttemptId(), userId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new CustomException("This attempt has already been " + attempt.getStatus(),
                    HttpStatus.CONFLICT);
        }

        Category category = categoryRepository.findById(attempt.getCategoryId())
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));

        // The section drives both the display name (never the real, possibly-rotated category name)
        // and the target size used below. Pre-migration attempts have no section on record; falling
        // back to the category's own name/minimum keeps them scorable rather than throwing.
        AssessmentSection section = attempt.getSection() == null ? null
                : AssessmentSection.valueOf(attempt.getSection());
        String categoryName = section != null ? section.getDisplayName() : category.getName();
        int targetSize = section != null ? section.getTargetSize()
                : AssessmentConstants.MIN_QUESTIONS_PER_CATEGORY;

        // Two queries for the whole category, then every per-answer check runs in memory.
        // Looking each question and option up individually would be 2 queries per answer.
        List<Question> questions =
                questionRepository.findByCategoryIdAndIsActiveTrueOrderByOrderIndexAsc(category.getId());
        Map<Long, Option> optionsById = loadOptions(questions).stream()
                .collect(Collectors.toMap(Option::getId, Function.identity()));
        Set<Long> validQuestionIds = questions.stream().map(Question::getId).collect(Collectors.toSet());

        List<AttemptAnswer> answers = validateAndScore(request.getAnswers(), validQuestionIds, optionsById,
                attempt.getId(), targetSize);
        attemptAnswerRepository.saveAll(answers);

        int rawScore = ScoringEngine.calculateRawScore(answers);
        // Denominator is the fixed size of the subset the student was shown -- NOT the whole
        // category (which can be larger, e.g. Programming Fundamentals backing Domain Knowledge, so
        // answering everything shown would still score well under 100%) and NOT the answer count
        // (which the client controls, so one perfect answer would be 100%). A partial submission
        // still scores lower, because unanswered questions earn nothing against a fixed denominator.
        int maxPossibleScore = ScoringEngine.calculateMaxPossibleScore(targetSize);
        double percentage = ScoringEngine.calculateCategoryScorePercentage(rawScore, maxPossibleScore);

        List<CareerPath> allCareers = careerPathRepository.findAll();
        // Relevance is matched against keywords, not the raw picked category name -- career
        // requiredSkills lists real skill words ("Programming", "Database"), which no section label
        // ("Domain Knowledge") will ever contain, so matching on the display name always tied every
        // career at 0.3.
        //
        // For DOMAIN_KNOWLEDGE specifically, this is the UNION of every pool member's name
        // ("Domain Knowledge", "Programming Fundamentals", "Database & SQL"), not just whichever one
        // startAttempt happened to draw for this attempt. An earlier version matched only the drawn
        // category and got this backwards: since relevanceFor() never looks at the student's actual
        // answers (only categoryScorePercentage does, and it scales every career by the same factor),
        // two students who answer identically but draw different pool members got different top
        // matches purely from that random draw -- a coin flip, not "real signal from different
        // content" as previously reasoned here. Frontend/Mobile Developer, whose requiredSkills lists
        // are shortest, structurally won that coin flip whenever "Programming Fundamentals" was drawn,
        // for every student, regardless of performance. Unioning the pool makes the ranking a fixed
        // function of the section (real, reproducible signal) instead of the draw (noise); the
        // student's actual score still scales every career's magnitude exactly as before.
        // Kept as its own variable, not inlined into getTopCareers: the full map goes out on the
        // event so recommendation-service can rank every career, while only the top N is persisted
        // and returned over HTTP.
        String relevanceSource = section == AssessmentSection.DOMAIN_KNOWLEDGE
                ? String.join(" ", AssessmentConstants.SECTION_CATEGORY_POOL.get(AssessmentSection.DOMAIN_KNOWLEDGE))
                : category.getName();
        Map<String, Double> allCareerScores =
                ScoringEngine.calculateCareerMatches(relevanceSource, percentage, allCareers);
        Map<String, Double> topCareers = ScoringEngine.getTopCareers(
                allCareerScores, AssessmentConstants.TOP_CAREERS_TO_RECOMMEND);

        // getTopCareers returns a LinkedHashMap in descending order, so the winner is the first entry.
        Map.Entry<String, Double> winner = topCareers.entrySet().stream().findFirst().orElse(null);
        Long topCareerPathId = winner == null ? null : resolveCareerId(allCareers, winner.getKey());

        AssessmentResult result = resultRepository.save(AssessmentResult.builder()
                .attemptId(attempt.getId())
                .userId(userId)
                .categoryId(category.getId())
                .rawScore(rawScore)
                .maxPossibleScore(maxPossibleScore)
                .categoryScorePercentage(percentage)
                .topCareerPathId(topCareerPathId)
                .careerMatchPercentage(winner == null ? null : winner.getValue())
                // FULL map, not just the top N -- needed so publishIfFinalSection can average every
                // career's score across all 3 sections once Soft Skills finishes. GET /result still
                // returns only the top N; it slices this down at read time instead.
                .allCareerScoresJson(toJson(allCareerScores))
                .build());

        attempt.setStatus(AttemptStatus.COMPLETED);
        attempt.setCompletedAt(LocalDateTime.now());
        attemptRepository.save(attempt);

        // Only the final section publishes AND returns the aggregate -- everything else about this
        // section's own persisted AssessmentResult (rawScore, maxPossibleScore, its own topCareers)
        // stays exactly as computed above; the aggregate is a presentation-layer combination on top,
        // not a replacement for the per-section record.
        if (section == AssessmentSection.SOFT_SKILLS) {
            AggregateOutcome aggregate = computeAggregate(attempt, result);
            publishCompleted(result, "Overall", aggregate.categoryScorePercentage(),
                    aggregate.topCareerPath(), aggregate.careerMatchPercentage(), aggregate.allCareerScores());
            return AssessmentResultDto.builder()
                    .attemptId(result.getAttemptId())
                    .userId(result.getUserId())
                    .categoryName("Overall")
                    .rawScore(aggregate.rawScore())
                    .maxPossibleScore(aggregate.maxPossibleScore())
                    .categoryScorePercentage(aggregate.categoryScorePercentage())
                    .topCareerPath(aggregate.topCareerPath())
                    .careerMatchPercentage(aggregate.careerMatchPercentage())
                    .allCareerScores(ScoringEngine.getTopCareers(
                            aggregate.allCareerScores(), AssessmentConstants.TOP_CAREERS_TO_RECOMMEND))
                    .calculatedAt(result.getCalculatedAt())
                    .build();
        }

        return toDto(result, categoryName, winner == null ? null : winner.getKey(), topCareers);
    }

    @Override
    @Transactional(readOnly = true)
    public AssessmentResultDto getResult(Long userId, Long attemptId) {
        AssessmentAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        AssessmentResult result = resultRepository.findByAttemptId(attempt.getId())
                .orElseThrow(() -> new CustomException(
                        "This attempt has not been submitted yet", HttpStatus.NOT_FOUND));

        String categoryName = attempt.getSection() != null
                ? AssessmentSection.valueOf(attempt.getSection()).getDisplayName()
                : categoryRepository.findById(result.getCategoryId())
                        .map(Category::getName)
                        .orElse(null);

        // allCareerScoresJson now stores the full per-career map (see submitAttempt); this endpoint's
        // contract has always been top-N only, so slice it down here rather than changing the response.
        Map<String, Double> scores = ScoringEngine.getTopCareers(
                fromJson(result.getAllCareerScoresJson()), AssessmentConstants.TOP_CAREERS_TO_RECOMMEND);
        String topCareerPath = result.getTopCareerPathId() == null ? null
                : careerPathRepository.findById(result.getTopCareerPathId())
                        .map(CareerPath::getName)
                        .orElse(null);

        return toDto(result, categoryName, topCareerPath, scores);
    }

    /**
     * Every rejection here is a 400: the payload is malformed relative to the attempt, not a
     * server fault. weightEarned always comes from the stored Option, never from the request --
     * a client that could send weights could fabricate its own score.
     */
    private List<AttemptAnswer> validateAndScore(List<AnswerDto> submitted,
                                                 Set<Long> validQuestionIds,
                                                 Map<Long, Option> optionsById,
                                                 Long attemptId,
                                                 int targetSize) {
        // Caps the numerator against a fixed denominator. The attempt showed exactly targetSize
        // questions, but every question in the category passes the membership check below --
        // without this a client could submit all of them and score far above 100%.
        if (submitted.size() > targetSize) {
            throw new CustomException(
                    "An attempt accepts at most " + targetSize + " answers",
                    HttpStatus.BAD_REQUEST);
        }

        List<AttemptAnswer> answers = new ArrayList<>(submitted.size());
        Set<Long> seenQuestionIds = new HashSet<>();

        for (AnswerDto answer : submitted) {
            if (!validQuestionIds.contains(answer.getQuestionId())) {
                throw new CustomException(
                        "Question " + answer.getQuestionId() + " is not part of this assessment",
                        HttpStatus.BAD_REQUEST);
            }
            // Without this, two answers for one question both add to rawScore and the total can
            // exceed maxPossibleScore.
            if (!seenQuestionIds.add(answer.getQuestionId())) {
                throw new CustomException(
                        "Duplicate answer for question " + answer.getQuestionId(),
                        HttpStatus.BAD_REQUEST);
            }

            Option option = optionsById.get(answer.getSelectedOptionId());
            if (option == null) {
                throw new CustomException("Option " + answer.getSelectedOptionId() + " not found",
                        HttpStatus.BAD_REQUEST);
            }
            // Scoring-integrity check: without it a client attaches another question's
            // highest-weighted option to an easy question.
            if (!option.getQuestionId().equals(answer.getQuestionId())) {
                throw new CustomException(
                        "Option " + option.getId() + " does not belong to question " + answer.getQuestionId(),
                        HttpStatus.BAD_REQUEST);
            }

            answers.add(AttemptAnswer.builder()
                    .attemptId(attemptId)
                    .questionId(answer.getQuestionId())
                    .selectedOptionId(option.getId())
                    .weightEarned(option.getWeight())
                    .build());
        }

        return answers;
    }

    /**
     * Draws targetSize questions at random from the category. For a section's dedicated category
     * (sized to match exactly) this serves the whole pool; for a shared pool member with more
     * questions than the target (e.g. Programming Fundamentals backing Domain Knowledge) it also
     * trims to a random subset, which is extra retake variety on top of the pool-level rotation.
     */
    private List<Question> selectQuestionsForAttempt(Long categoryId, int targetSize) {
        List<Question> pool = shuffled(
                questionRepository.findByCategoryIdAndIsActiveTrueOrderByOrderIndexAsc(categoryId));
        return pool.size() <= targetSize ? pool : new ArrayList<>(pool.subList(0, targetSize));
    }

    /**
     * Maps questions to DTOs with their options shuffled.
     *
     * orderIndex is renumbered to the position in THIS response rather than passed through from the
     * database. That is a security fix, not cosmetics: in the seed data the highest-weighted option
     * is always order_index 1, so echoing the stored index would let a client sort by it and pick
     * the best answer every time -- defeating both the option shuffle and the rule that weights
     * never reach the client.
     */
    private List<QuestionDto> toQuestionDtos(List<Question> questions) {
        Map<Long, List<Option>> optionsByQuestion = loadOptions(questions).stream()
                .collect(Collectors.groupingBy(Option::getQuestionId));

        return IntStream.range(0, questions.size())
                .mapToObj(qi -> {
                    Question q = questions.get(qi);
                    List<Option> options = shuffled(optionsByQuestion.getOrDefault(q.getId(), List.of()));
                    return QuestionDto.builder()
                            .questionId(q.getId())
                            .questionText(q.getQuestionText())
                            .orderIndex(qi + 1)
                            .options(IntStream.range(0, options.size())
                                    // No weight is mapped -- OptionDto has no such field, by design.
                                    .mapToObj(oi -> OptionDto.builder()
                                            .optionId(options.get(oi).getId())
                                            .optionText(options.get(oi).getOptionText())
                                            .orderIndex(oi + 1)
                                            .build())
                                    .toList())
                            .build();
                })
                .toList();
    }

    /** Shuffles a defensive copy -- the repository list must not be reordered in place. */
    private static <T> List<T> shuffled(List<T> source) {
        List<T> copy = new ArrayList<>(source);
        Collections.shuffle(copy, new Random());
        return copy;
    }

    /** One query for every option in the category; empty question list short-circuits. */
    private List<Option> loadOptions(List<Question> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        return optionRepository.findByQuestionIdInOrderByOrderIndex(
                questions.stream().map(Question::getId).toList());
    }

    private Long resolveCareerId(List<CareerPath> allCareers, String name) {
        return allCareers.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .map(CareerPath::getId)
                .orElse(null);
    }

    /** categoryScorePercentage/allCareerScores here are already the true 3-section blend, not one section's. */
    private record AggregateOutcome(int rawScore, int maxPossibleScore, double categoryScorePercentage,
                                    String topCareerPath, Double careerMatchPercentage,
                                    Map<String, Double> allCareerScores) {
    }

    /**
     * Only called for the final section (Soft Skills) -- gathers the user's latest completed Aptitude
     * and Domain Knowledge results alongside this one and blends all 3 into a single outcome, used for
     * BOTH the published event and the HTTP response, so the website and the recommendation email are
     * never computed from two different numbers again.
     *
     * The percentage is rawScore/maxPossibleScore summed across all 3 sections (properly weighted by
     * each section's real size -- 5/10/5), NOT a flat average of 3 percentages: a flat average would
     * treat the 10-question Domain Knowledge section as equally important as either 5-question one,
     * silently under-weighting it relative to what the student actually answered.
     */
    private AggregateOutcome computeAggregate(AssessmentAttempt attempt, AssessmentResult softSkillsResult) {
        List<AssessmentResult> sectionResults = new ArrayList<>();
        sectionResults.add(softSkillsResult);
        for (AssessmentSection prior : List.of(AssessmentSection.APTITUDE, AssessmentSection.DOMAIN_KNOWLEDGE)) {
            attemptRepository.findTopByUserIdAndSectionAndStatusOrderByCompletedAtDesc(
                            attempt.getUserId(), prior.name(), AttemptStatus.COMPLETED)
                    .flatMap(a -> resultRepository.findByAttemptId(a.getId()))
                    .ifPresent(sectionResults::add);
        }

        int totalRaw = sectionResults.stream().mapToInt(AssessmentResult::getRawScore).sum();
        int totalMax = sectionResults.stream().mapToInt(AssessmentResult::getMaxPossibleScore).sum();
        double pct = ScoringEngine.calculateCategoryScorePercentage(totalRaw, totalMax);

        // Average each career's score across however many of the 3 sections were found -- every
        // section scores the same 7 careers (careerPathRepository.findAll() is shared), so the key
        // sets agree and this is a plain per-career mean, not a merge of disjoint data.
        Map<String, Double> sums = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (AssessmentResult r : sectionResults) {
            fromJson(r.getAllCareerScoresJson()).forEach((career, score) -> {
                sums.merge(career, score, Double::sum);
                counts.merge(career, 1, Integer::sum);
            });
        }
        Map<String, Double> combined = new HashMap<>();
        sums.forEach((career, sum) -> combined.put(career,
                Math.round((sum / counts.get(career)) * 100.0) / 100.0));

        Map<String, Double> topCombined = ScoringEngine.getTopCareers(
                combined, AssessmentConstants.TOP_CAREERS_TO_RECOMMEND);
        Map.Entry<String, Double> winner = topCombined.entrySet().stream().findFirst().orElse(null);

        return new AggregateOutcome(totalRaw, totalMax, pct,
                winner == null ? null : winner.getKey(),
                winner == null ? null : winner.getValue(),
                combined);
    }

    /**
     * Fail-soft: a broker outage must not cost the student their submitted assessment.
     *
     * ponytail: publishes before the surrounding transaction commits, so a rollback after this
     * point would leave a phantom event. Kept last in submitAttempt to shrink that window; move to
     * @TransactionalEventListener(AFTER_COMMIT) if exactly-once delivery starts mattering.
     */
    private void publishCompleted(AssessmentResult identity, String categoryName,
                                  Double categoryScorePercentage, String topCareerPath,
                                  Double careerMatchPercentage, Map<String, Double> allCareerScores) {
        try {
            rabbitTemplate.convertAndSend(
                    AssessmentConstants.EXCHANGE_NAME,
                    AssessmentConstants.ROUTING_KEY_ASSESSMENT_COMPLETED,
                    AssessmentCompletedEvent.builder()
                            .userId(identity.getUserId())
                            .attemptId(identity.getAttemptId())
                            .categoryId(identity.getCategoryId())
                            .categoryName(categoryName)
                            .categoryScorePercentage(categoryScorePercentage)
                            .topCareerPath(topCareerPath)
                            .careerMatchPercentage(careerMatchPercentage)
                            .completedAt(LocalDateTime.now())
                            .allCareerScores(allCareerScores)
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for attemptId={}: {}",
                    AssessmentConstants.ROUTING_KEY_ASSESSMENT_COMPLETED,
                    identity.getAttemptId(), ex.getMessage());
        }
    }

    private AssessmentResultDto toDto(AssessmentResult result,
                                      String categoryName,
                                      String topCareerPath,
                                      Map<String, Double> careerScores) {
        return AssessmentResultDto.builder()
                .attemptId(result.getAttemptId())
                .userId(result.getUserId())
                .categoryName(categoryName)
                .rawScore(result.getRawScore())
                .maxPossibleScore(result.getMaxPossibleScore())
                .categoryScorePercentage(result.getCategoryScorePercentage())
                .topCareerPath(topCareerPath)
                .careerMatchPercentage(result.getCareerMatchPercentage())
                .allCareerScores(careerScores)
                .calculatedAt(result.getCalculatedAt())
                .build();
    }

    // No try/catch: Jackson 3's JacksonException is unchecked, and a Map<String,Double> that fails
    // to serialise is a bug in this service, not a client error -- let it surface as a 500.
    private String toJson(Map<String, Double> careerScores) {
        return MAPPER.writeValueAsString(careerScores);
    }

    private Map<String, Double> fromJson(String json) {
        return StringUtils.hasText(json) ? MAPPER.readValue(json, CAREER_SCORES_TYPE) : Map.of();
    }

    /** countByCategoryIdAndIsActiveTrue is an Integer, so a null from an empty table reads as zero. */
    private int safeCount(Integer count) {
        return count == null ? 0 : count;
    }
}
