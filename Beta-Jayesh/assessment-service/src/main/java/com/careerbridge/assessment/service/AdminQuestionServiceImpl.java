package com.careerbridge.assessment.service;

import com.careerbridge.assessment.constants.AssessmentConstants;
import com.careerbridge.assessment.dto.AdminOptionRequest;
import com.careerbridge.assessment.dto.AdminOptionResponse;
import com.careerbridge.assessment.dto.AdminQuestionRequest;
import com.careerbridge.assessment.dto.AdminQuestionResponse;
import com.careerbridge.assessment.exception.CustomException;
import com.careerbridge.assessment.model.Category;
import com.careerbridge.assessment.model.Option;
import com.careerbridge.assessment.model.Question;
import com.careerbridge.assessment.repository.CategoryRepository;
import com.careerbridge.assessment.repository.OptionRepository;
import com.careerbridge.assessment.repository.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminQuestionServiceImpl implements AdminQuestionService {

    private static final Logger log = LoggerFactory.getLogger(AdminQuestionServiceImpl.class);

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ORG_ADMIN = "ORG_ADMIN";

    private final QuestionRepository questionRepository;
    private final OptionRepository optionRepository;
    private final CategoryRepository categoryRepository;

    public AdminQuestionServiceImpl(QuestionRepository questionRepository,
                                    OptionRepository optionRepository,
                                    CategoryRepository categoryRepository) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.categoryRepository = categoryRepository;
    }

    // ---------------------------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------------------------

    @Override
    @Transactional
    public AdminQuestionResponse addQuestion(String callerRole, AdminQuestionRequest request) {
        requireAdmin(callerRole);

        Category category = requireCategory(request.getCategoryId());
        requireExactlyOneMaxWeight(request.getOptions());

        Question saved = questionRepository.save(Question.builder()
                .categoryId(category.getId())
                .questionText(request.getText())
                .orderIndex(request.getOrderIndex())
                // Null-safe rather than trusting the DTO's @Builder.Default: a JSON body with an
                // explicit "isActive": null beats the field initialiser.
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build());

        List<Option> options = saveOptions(saved.getId(), request.getOptions());

        log.info("Question {} added to category {} with {} options",
                saved.getId(), category.getId(), options.size());

        return toResponse(saved, category.getName(), options);
    }

    /**
     * Replaces the question's whole option set rather than diffing it: an admin edit is a
     * declaration of what the options now are, and matching old rows to new ones would need a stable
     * client-side identity the request does not carry.
     *
     * Consequence, and it is deliberate: option ids change on every edit. Nothing references an
     * option id across time -- AttemptAnswer stores the weight it awarded, not a live FK -- so a
     * historical attempt keeps its original score.
     */
    @Override
    @Transactional
    public AdminQuestionResponse editQuestion(String callerRole, Long questionId,
                                              AdminQuestionRequest request) {
        requireAdmin(callerRole);

        Question question = requireQuestion(questionId);
        Category category = requireCategory(request.getCategoryId());
        requireExactlyOneMaxWeight(request.getOptions());

        question.setCategoryId(category.getId());
        question.setQuestionText(request.getText());
        question.setOrderIndex(request.getOrderIndex());
        if (request.getIsActive() != null) {
            question.setIsActive(request.getIsActive());
        }
        Question saved = questionRepository.save(question);

        // Delete then insert. No cascade exists to lean on: Question holds no @OneToMany to Option,
        // only a plain question_id column on the Option side.
        optionRepository.deleteByQuestionId(questionId);
        List<Option> options = saveOptions(questionId, request.getOptions());

        log.info("Question {} edited; {} options replaced", questionId, options.size());

        return toResponse(saved, category.getName(), options);
    }

    @Override
    @Transactional
    public void activateQuestion(String callerRole, Long questionId) {
        requireAdmin(callerRole);

        Question question = requireQuestion(questionId);
        if (Boolean.TRUE.equals(question.getIsActive())) {
            throw new CustomException("Question is already active", HttpStatus.BAD_REQUEST);
        }

        question.setIsActive(true);
        questionRepository.save(question);

        log.info("Question {} reactivated", questionId);
    }

    /**
     * Soft-retires a question. Deliberately does NOT check whether this drops the category below
     * MIN_QUESTIONS_PER_CATEGORY: blocking the edit would leave an admin unable to retire a wrong
     * question without first writing a replacement. startAttempt already fails cleanly with
     * "too few questions" in that state, which is the honest outcome.
     */
    @Override
    @Transactional
    public void deactivateQuestion(String callerRole, Long questionId) {
        requireAdmin(callerRole);

        Question question = requireQuestion(questionId);
        if (!Boolean.TRUE.equals(question.getIsActive())) {
            throw new CustomException("Question is already inactive", HttpStatus.BAD_REQUEST);
        }

        question.setIsActive(false);
        Question saved = questionRepository.save(question);

        // Warn when the category can no longer start an assessment -- silent otherwise, and an admin
        // would only discover it when a student hit the 400.
        long remaining = safeCount(
                questionRepository.countByCategoryIdAndIsActiveTrue(saved.getCategoryId()));
        if (remaining < AssessmentConstants.MIN_QUESTIONS_PER_CATEGORY) {
            log.warn("Category {} now has {} active questions, below the minimum of {} -- "
                            + "startAttempt will refuse it until more are activated",
                    saved.getCategoryId(), remaining, AssessmentConstants.MIN_QUESTIONS_PER_CATEGORY);
        }

        log.info("Question {} deactivated", questionId);
    }

    // ---------------------------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AdminQuestionResponse getQuestion(String callerRole, Long questionId) {
        requireAdmin(callerRole);

        Question question = requireQuestion(questionId);
        return toResponse(question, categoryName(question.getCategoryId()), loadOptions(questionId));
    }

    /**
     * Three queries total regardless of question count -- questions, categories, options -- then
     * assembled in memory. The naive shape would be one category lookup and one option lookup per
     * question, i.e. 2N+1 round trips across the whole bank.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AdminQuestionResponse> listQuestions(String callerRole, Long categoryId) {
        requireAdmin(callerRole);

        // Deliberately unfiltered on isActive: this is the one query in the service that must return
        // retired questions, since an admin cannot reactivate what they cannot see.
        List<Question> questions = questionRepository.findAllByOrderByCategoryIdAscOrderIndexAsc();

        if (categoryId != null) {
            // Objects.equals, never ==: categoryId is a boxed Long and real ids sit outside the
            // Integer cache, so reference comparison would filter everything out.
            questions = questions.stream()
                    .filter(q -> Objects.equals(q.getCategoryId(), categoryId))
                    .toList();
        }

        if (questions.isEmpty()) {
            return List.of();
        }

        Map<Long, String> categoryNames = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        Map<Long, List<Option>> optionsByQuestion =
                optionRepository.findByQuestionIdInOrderByOrderIndex(questionIds).stream()
                        .collect(Collectors.groupingBy(Option::getQuestionId));

        List<AdminQuestionResponse> responses = new ArrayList<>(questions.size());
        for (Question question : questions) {
            responses.add(toResponse(
                    question,
                    categoryNames.get(question.getCategoryId()),
                    // A question with no options is malformed data rather than an error to throw on;
                    // returning it with an empty list is what lets an admin see and fix it.
                    optionsByQuestion.getOrDefault(question.getId(), List.of())));
        }

        return responses;
    }

    // ---------------------------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------------------------

    private void requireAdmin(String callerRole) {
        if (!ROLE_SUPER_ADMIN.equals(callerRole) && !ROLE_ORG_ADMIN.equals(callerRole)) {
            throw new CustomException("Only SUPER_ADMIN or ORG_ADMIN may manage the question bank",
                    HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Exactly one option must carry MAX_OPTION_WEIGHT.
     *
     * Zero would make the question unanswerable at full marks and quietly lower every attempt's
     * ceiling; more than one would let two different answers both score maximum, which the graded
     * 0..3 model does not mean. Verified against the seeded bank before being enforced: all 22
     * existing questions already satisfy it, so no admin edit of pre-existing data trips this.
     *
     * intValue() against the int constant, never == on the boxed Integer: Integer caches only
     * -128..127, so reference comparison happens to work for 3 today and would break silently the
     * day MAX_OPTION_WEIGHT rose above 127.
     */
    private void requireExactlyOneMaxWeight(List<AdminOptionRequest> options) {
        long maxWeightCount = options.stream()
                .filter(o -> o.getWeight() != null
                        && o.getWeight().intValue() == AssessmentConstants.MAX_OPTION_WEIGHT)
                .count();

        if (maxWeightCount != 1) {
            throw new CustomException(
                    "Exactly one option must have the maximum weight ("
                            + AssessmentConstants.MAX_OPTION_WEIGHT + ").",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException("Category not found: " + categoryId,
                        HttpStatus.NOT_FOUND));
    }

    /** Plain findById, not an active-filtered finder: an admin must be able to reach retired rows. */
    private Question requireQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("Question not found: " + questionId,
                        HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence and mapping helpers
    // ---------------------------------------------------------------------------------------------

    /** orderIndex comes from list position, so the stored order always matches what was submitted. */
    private List<Option> saveOptions(Long questionId, List<AdminOptionRequest> requested) {
        List<Option> toSave = new ArrayList<>(requested.size());
        for (int i = 0; i < requested.size(); i++) {
            AdminOptionRequest option = requested.get(i);
            toSave.add(Option.builder()
                    .questionId(questionId)
                    .optionText(option.getText())
                    .weight(option.getWeight())
                    .orderIndex(i + 1)
                    .build());
        }
        return optionRepository.saveAll(toSave);
    }

    private List<Option> loadOptions(Long questionId) {
        return optionRepository.findByQuestionIdOrderByOrderIndex(questionId);
    }

    private String categoryName(Long categoryId) {
        return categoryRepository.findById(categoryId).map(Category::getName).orElse(null);
    }

    /** countByCategoryIdAndIsActiveTrue is an Integer, so a null from an empty table reads as zero. */
    private int safeCount(Integer count) {
        return count == null ? 0 : count;
    }

    private AdminQuestionResponse toResponse(Question question, String categoryName,
                                             List<Option> options) {
        return AdminQuestionResponse.builder()
                .id(question.getId())
                .categoryId(question.getCategoryId())
                .categoryName(categoryName)
                .text(question.getQuestionText())
                .orderIndex(question.getOrderIndex())
                .isActive(question.getIsActive())
                .options(options.stream().map(this::toResponse).toList())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }

    private AdminOptionResponse toResponse(Option option) {
        return AdminOptionResponse.builder()
                .id(option.getId())
                .text(option.getOptionText())
                .weight(option.getWeight())
                .build();
    }
}
