package com.careerbridge.assessment.service;

import com.careerbridge.assessment.constants.AssessmentConstants;
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
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));

        // Fail before the student answers anything: a thin category cannot produce a meaningful
        // percentage, and an empty one would divide into a 0% score that looks like a real result.
        // Counts ACTIVE questions only, matching selectQuestionsForAttempt's pool exactly. If this
        // counted retired questions the guard could pass on 6 while the pool served 3, and the fixed
        // maxPossibleScore of 15 would cap the student at 60% with nothing logged.
        int questionCount = safeCount(
                questionRepository.countByCategoryIdAndIsActiveTrue(category.getId()));
        if (questionCount < AssessmentConstants.MIN_QUESTIONS_PER_CATEGORY) {
            throw new CustomException(
                    "Category '" + category.getName() + "' has too few questions to assess",
                    HttpStatus.BAD_REQUEST);
        }

        attemptRepository.findByUserIdAndCategoryIdAndStatus(
                        userId, category.getId(), AttemptStatus.IN_PROGRESS)
                .ifPresent(existing -> {
                    throw new CustomException(
                            "An assessment for this category is already in progress",
                            HttpStatus.CONFLICT);
                });

        AssessmentAttempt attempt = attemptRepository.save(AssessmentAttempt.builder()
                .userId(userId)
                .categoryId(category.getId())
                .status(AttemptStatus.IN_PROGRESS)
                .build());

        // Reshuffled per attempt: two students -- or one student retrying -- do not get the same
        // questions in the same order.
        return AssessmentResponse.builder()
                .attemptId(attempt.getId())
                .categoryId(category.getId())
                .categoryName(category.getName())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .questions(toQuestionDtos(selectQuestionsForAttempt(category.getId())))
                .build();
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

        // Two queries for the whole category, then every per-answer check runs in memory.
        // Looking each question and option up individually would be 2 queries per answer.
        List<Question> questions =
                questionRepository.findByCategoryIdAndIsActiveTrueOrderByOrderIndexAsc(category.getId());
        Map<Long, Option> optionsById = loadOptions(questions).stream()
                .collect(Collectors.toMap(Option::getId, Function.identity()));
        Set<Long> validQuestionIds = questions.stream().map(Question::getId).collect(Collectors.toSet());

        List<AttemptAnswer> answers = validateAndScore(request.getAnswers(), validQuestionIds, optionsById,
                attempt.getId());
        attemptAnswerRepository.saveAll(answers);

        int rawScore = ScoringEngine.calculateRawScore(answers);
        // Denominator is the fixed size of the subset the student was shown -- NOT the whole
        // category (which is larger since questions are drawn at random, so answering everything
        // shown would still score well under 100%) and NOT the answer count (which the client
        // controls, so one perfect answer would be 100%). A partial submission still scores lower,
        // because unanswered questions earn nothing against a fixed denominator.
        int maxPossibleScore =
                ScoringEngine.calculateMaxPossibleScore(AssessmentConstants.QUESTIONS_PER_ATTEMPT);
        double percentage = ScoringEngine.calculateCategoryScorePercentage(rawScore, maxPossibleScore);

        List<CareerPath> allCareers = careerPathRepository.findAll();
        // Kept as its own variable, not inlined into getTopCareers: the full map goes out on the
        // event so recommendation-service can rank every career, while only the top N is persisted
        // and returned over HTTP.
        Map<String, Double> allCareerScores =
                ScoringEngine.calculateCareerMatches(category.getName(), percentage, allCareers);
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
                .allCareerScoresJson(toJson(topCareers))
                .build());

        attempt.setStatus(AttemptStatus.COMPLETED);
        attempt.setCompletedAt(LocalDateTime.now());
        attemptRepository.save(attempt);

        publishCompleted(result, category.getName(), winner == null ? null : winner.getKey(),
                allCareerScores);

        return toDto(result, category.getName(), winner == null ? null : winner.getKey(), topCareers);
    }

    @Override
    @Transactional(readOnly = true)
    public AssessmentResultDto getResult(Long userId, Long attemptId) {
        AssessmentAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new CustomException("Attempt not found", HttpStatus.NOT_FOUND));

        AssessmentResult result = resultRepository.findByAttemptId(attempt.getId())
                .orElseThrow(() -> new CustomException(
                        "This attempt has not been submitted yet", HttpStatus.NOT_FOUND));

        String categoryName = categoryRepository.findById(result.getCategoryId())
                .map(Category::getName)
                .orElse(null);

        Map<String, Double> scores = fromJson(result.getAllCareerScoresJson());
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
                                                 Long attemptId) {
        // Caps the numerator against a fixed denominator. The attempt showed exactly
        // QUESTIONS_PER_ATTEMPT questions, but every question in the category passes the
        // membership check below -- without this a client could submit all of them and score
        // far above 100%.
        if (submitted.size() > AssessmentConstants.QUESTIONS_PER_ATTEMPT) {
            throw new CustomException(
                    "An attempt accepts at most " + AssessmentConstants.QUESTIONS_PER_ATTEMPT + " answers",
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

    /** Draws QUESTIONS_PER_ATTEMPT questions at random from the category. */
    private List<Question> selectQuestionsForAttempt(Long categoryId) {
        List<Question> pool = shuffled(
                questionRepository.findByCategoryIdAndIsActiveTrueOrderByOrderIndexAsc(categoryId));
        return pool.size() <= AssessmentConstants.QUESTIONS_PER_ATTEMPT
                ? pool
                : new ArrayList<>(pool.subList(0, AssessmentConstants.QUESTIONS_PER_ATTEMPT));
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

    /**
     * Fail-soft: a broker outage must not cost the student their submitted assessment.
     *
     * ponytail: publishes before the surrounding transaction commits, so a rollback after this
     * point would leave a phantom event. Kept last in submitAttempt to shrink that window; move to
     * @TransactionalEventListener(AFTER_COMMIT) if exactly-once delivery starts mattering.
     */
    private void publishCompleted(AssessmentResult result, String categoryName, String topCareerPath,
                                  Map<String, Double> allCareerScores) {
        try {
            rabbitTemplate.convertAndSend(
                    AssessmentConstants.EXCHANGE_NAME,
                    AssessmentConstants.ROUTING_KEY_ASSESSMENT_COMPLETED,
                    AssessmentCompletedEvent.builder()
                            .userId(result.getUserId())
                            .attemptId(result.getAttemptId())
                            .categoryId(result.getCategoryId())
                            .categoryName(categoryName)
                            .categoryScorePercentage(result.getCategoryScorePercentage())
                            .topCareerPath(topCareerPath)
                            .careerMatchPercentage(result.getCareerMatchPercentage())
                            .completedAt(LocalDateTime.now())
                            .allCareerScores(allCareerScores)
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for attemptId={}: {}",
                    AssessmentConstants.ROUTING_KEY_ASSESSMENT_COMPLETED,
                    result.getAttemptId(), ex.getMessage());
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
