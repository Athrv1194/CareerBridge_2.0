package com.careerbridge.assessment;

import com.careerbridge.assessment.constants.AssessmentConstants;
import com.careerbridge.assessment.dto.AnswerDto;
import com.careerbridge.assessment.dto.AssessmentRequest;
import com.careerbridge.assessment.dto.AssessmentResponse;
import com.careerbridge.assessment.dto.AssessmentResultDto;
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
import com.careerbridge.assessment.service.AssessmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long CATEGORY_ID = 7L;
    private static final Long ATTEMPT_ID = 100L;

    @Mock private CategoryRepository categoryRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private OptionRepository optionRepository;
    @Mock private CareerPathRepository careerPathRepository;
    @Mock private AssessmentAttemptRepository attemptRepository;
    @Mock private AttemptAnswerRepository attemptAnswerRepository;
    @Mock private AssessmentResultRepository resultRepository;
    @Mock private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @InjectMocks private AssessmentServiceImpl assessmentService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(CATEGORY_ID)
                .name("Logical Reasoning")
                .description("Pattern and deduction questions")
                .build();
    }

    /** N questions, each with two options weighted 3 and 1. Option ids are questionId * 10 + n. */
    private List<Question> questions(int n) {
        List<Question> questions = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            questions.add(Question.builder()
                    .id((long) i)
                    .categoryId(CATEGORY_ID)
                    .questionText("Question " + i)
                    .orderIndex(i)
                    .build());
        }
        return questions;
    }

    private List<Question> fiveQuestions() {
        return questions(AssessmentConstants.QUESTIONS_PER_ATTEMPT);
    }

    private List<Option> optionsFor(List<Question> questions) {
        List<Option> options = new ArrayList<>();
        for (Question q : questions) {
            options.add(Option.builder().id(q.getId() * 10 + 1).questionId(q.getId())
                    .optionText("Best").weight(3).orderIndex(1).build());
            options.add(Option.builder().id(q.getId() * 10 + 2).questionId(q.getId())
                    .optionText("Weak").weight(1).orderIndex(2).build());
        }
        return options;
    }

    private AssessmentAttempt inProgressAttempt() {
        return AssessmentAttempt.builder()
                .id(ATTEMPT_ID)
                .userId(USER_ID)
                .categoryId(CATEGORY_ID)
                .status(AttemptStatus.IN_PROGRESS)
                .build();
    }

    private SubmitAnswerRequest submitAll(List<Question> questions, int optionSuffix) {
        SubmitAnswerRequest request = new SubmitAnswerRequest();
        request.setAttemptId(ATTEMPT_ID);
        List<AnswerDto> answers = new ArrayList<>();
        for (Question q : questions) {
            AnswerDto answer = new AnswerDto();
            answer.setQuestionId(q.getId());
            answer.setSelectedOptionId(q.getId() * 10 + optionSuffix);
            answers.add(answer);
        }
        request.setAnswers(answers);
        return request;
    }

    @Test
    @DisplayName("startAttempt: draws only the per-attempt subset, with options carrying no weight")
    void startAttempt_ValidCategory_ReturnsQuestionsWithoutWeights() {
        // Category holds more questions than an attempt uses, so the subset is observable.
        List<Question> pool = questions(8);
        AssessmentRequest request = new AssessmentRequest();
        request.setCategoryId(CATEGORY_ID);

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.countByCategoryId(CATEGORY_ID)).thenReturn(8);
        when(attemptRepository.findByUserIdAndCategoryIdAndStatus(
                USER_ID, CATEGORY_ID, AttemptStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(attemptRepository.save(any(AssessmentAttempt.class))).thenReturn(inProgressAttempt());
        when(questionRepository.findByCategoryIdOrderByOrderIndex(CATEGORY_ID)).thenReturn(pool);
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(optionsFor(pool));

        AssessmentResponse response = assessmentService.startAttempt(USER_ID, request);

        assertEquals(ATTEMPT_ID, response.getAttemptId());
        assertEquals("Logical Reasoning", response.getCategoryName());
        assertEquals(AttemptStatus.IN_PROGRESS, response.getStatus());
        // Exactly the subset, not all 8 that exist in the category.
        assertEquals(AssessmentConstants.QUESTIONS_PER_ATTEMPT, response.getQuestions().size());

        QuestionDto first = response.getQuestions().get(0);
        assertEquals(2, first.getOptions().size());
        assertNotNull(first.getOptions().get(0).getOptionText());
        // The security requirement is structural: OptionDto has no weight field at all, so there
        // is no getter to assert null on. This pins that no such field is ever reintroduced.
        assertFalse(java.util.Arrays.stream(first.getOptions().get(0).getClass().getDeclaredFields())
                .anyMatch(f -> f.getName().toLowerCase().contains("weight")));

        // orderIndex must be the position in THIS response, never the stored authoring order --
        // in the real seed data the top-weighted option is always stored at order_index 1, so
        // echoing it back would hand the client the correct answer despite the shuffle.
        assertEquals(List.of(1, 2, 3, 4, 5),
                response.getQuestions().stream().map(QuestionDto::getOrderIndex).toList());
        assertEquals(List.of(1, 2),
                first.getOptions().stream().map(OptionDto::getOrderIndex).toList());

        ArgumentCaptor<AssessmentAttempt> saved = ArgumentCaptor.forClass(AssessmentAttempt.class);
        verify(attemptRepository).save(saved.capture());
        assertEquals(AttemptStatus.IN_PROGRESS, saved.getValue().getStatus());
        assertEquals(USER_ID, saved.getValue().getUserId());
    }

    @Test
    @DisplayName("startAttempt: the drawn questions vary between attempts rather than being fixed")
    void startAttempt_QuestionsAreShuffled_DifferentOrderPossible() {
        List<Question> pool = questions(10);
        AssessmentRequest request = new AssessmentRequest();
        request.setCategoryId(CATEGORY_ID);

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.countByCategoryId(CATEGORY_ID)).thenReturn(10);
        when(attemptRepository.findByUserIdAndCategoryIdAndStatus(
                USER_ID, CATEGORY_ID, AttemptStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(attemptRepository.save(any(AssessmentAttempt.class))).thenReturn(inProgressAttempt());
        when(questionRepository.findByCategoryIdOrderByOrderIndex(CATEGORY_ID)).thenReturn(pool);
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(optionsFor(pool));

        Set<List<Long>> distinctDraws = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            distinctDraws.add(assessmentService.startAttempt(USER_ID, request).getQuestions().stream()
                    .map(QuestionDto::getQuestionId)
                    .toList());
        }

        // Probabilistic, but only nominally: there are 30240 ordered 5-of-10 draws, so ten
        // identical results would be a ~1-in-10^40 event. A failure here means the shuffle
        // is not running at all, not that the dice were unkind.
        assertTrue(distinctDraws.size() > 1,
                "Expected the question draw to vary across attempts, but all 10 were identical");
    }

    @Test
    @DisplayName("startAttempt: a second attempt while one is in progress is refused with 409")
    void startAttempt_AlreadyInProgress_ThrowsConflict() {
        AssessmentRequest request = new AssessmentRequest();
        request.setCategoryId(CATEGORY_ID);

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.countByCategoryId(CATEGORY_ID)).thenReturn(5);
        when(attemptRepository.findByUserIdAndCategoryIdAndStatus(
                USER_ID, CATEGORY_ID, AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(inProgressAttempt()));

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.startAttempt(USER_ID, request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(attemptRepository, never()).save(any(AssessmentAttempt.class));
    }

    @Test
    @DisplayName("startAttempt: a category thinner than the minimum is refused before any attempt is created")
    void startAttempt_TooFewQuestions_ThrowsBadRequest() {
        AssessmentRequest request = new AssessmentRequest();
        request.setCategoryId(CATEGORY_ID);

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.countByCategoryId(CATEGORY_ID)).thenReturn(3);

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.startAttempt(USER_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(attemptRepository, never()).save(any(AssessmentAttempt.class));
    }

    @Test
    @DisplayName("submitAttempt: scores every answer, ranks careers, completes the attempt and publishes")
    void submitAttempt_ValidAnswers_CalculatesScoreCorrectly() {
        List<Question> questions = fiveQuestions();

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(inProgressAttempt()));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.findByCategoryIdOrderByOrderIndex(CATEGORY_ID)).thenReturn(questions);
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(optionsFor(questions));
        when(careerPathRepository.findAll()).thenReturn(List.of(
                CareerPath.builder().id(1L).name("Data Scientist")
                        .requiredSkills("Python, Logical Reasoning").build(),
                CareerPath.builder().id(2L).name("Chef").requiredSkills("Cooking").build()));
        when(resultRepository.save(any(AssessmentResult.class))).thenAnswer(inv -> inv.getArgument(0));

        // Every question answered with the weight-3 option.
        AssessmentResultDto dto = assessmentService.submitAttempt(USER_ID, submitAll(questions, 1));

        ArgumentCaptor<AssessmentResult> saved = ArgumentCaptor.forClass(AssessmentResult.class);
        verify(resultRepository).save(saved.capture());
        AssessmentResult result = saved.getValue();

        assertEquals(15, result.getRawScore());
        assertEquals(15, result.getMaxPossibleScore());
        assertEquals(100.0, result.getCategoryScorePercentage());
        // "Data Scientist" lists the category in requiredSkills, so it wins at full relevance.
        assertEquals(1L, result.getTopCareerPathId());
        assertEquals(100.0, result.getCareerMatchPercentage());
        assertNotNull(result.getAllCareerScoresJson());

        assertEquals("Data Scientist", dto.getTopCareerPath());
        // Chef does not name the category in requiredSkills, so it is damped: 100.0 x 0.3.
        assertEquals(30.0, dto.getAllCareerScores().get("Chef"));

        // weightEarned is read from the stored option, never from the payload.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttemptAnswer>> answers = ArgumentCaptor.forClass(List.class);
        verify(attemptAnswerRepository).saveAll(answers.capture());
        assertEquals(5, answers.getValue().size());
        assertEquals(3, answers.getValue().get(0).getWeightEarned());

        ArgumentCaptor<AssessmentAttempt> completed = ArgumentCaptor.forClass(AssessmentAttempt.class);
        verify(attemptRepository).save(completed.capture());
        assertEquals(AttemptStatus.COMPLETED, completed.getValue().getStatus());
        assertNotNull(completed.getValue().getCompletedAt());

        verify(rabbitTemplate).convertAndSend(anyString(), eq("assessment.completed"), any(Object.class));
    }

    @Test
    @DisplayName("submitAttempt: the published event carries every career, not just the top N")
    void submitAttempt_PublishedEvent_CarriesAllCareerScores() {
        List<Question> questions = fiveQuestions();

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(inProgressAttempt()));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.findByCategoryIdOrderByOrderIndex(CATEGORY_ID)).thenReturn(questions);
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(optionsFor(questions));
        // Five careers against TOP_CAREERS_TO_RECOMMEND = 3, so "all" and "top N" cannot coincide.
        when(careerPathRepository.findAll()).thenReturn(List.of(
                CareerPath.builder().id(1L).name("Data Scientist")
                        .requiredSkills("Python, Logical Reasoning").build(),
                CareerPath.builder().id(2L).name("Chef").requiredSkills("Cooking").build(),
                CareerPath.builder().id(3L).name("Barista").requiredSkills("Espresso").build(),
                CareerPath.builder().id(4L).name("Sommelier").requiredSkills("Wine").build(),
                CareerPath.builder().id(5L).name("Florist").requiredSkills("Flowers").build()));
        when(resultRepository.save(any(AssessmentResult.class))).thenAnswer(inv -> inv.getArgument(0));

        AssessmentResultDto dto = assessmentService.submitAttempt(USER_ID, submitAll(questions, 1));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(anyString(), eq("assessment.completed"), payload.capture());

        AssessmentCompletedEvent event = (AssessmentCompletedEvent) payload.getValue();
        Map<String, Double> allScores = event.getAllCareerScores();

        assertNotNull(allScores);
        assertEquals(5, allScores.size(), "the event must not be truncated to the top N");
        assertTrue(allScores.keySet().containsAll(
                List.of("Data Scientist", "Chef", "Barista", "Sommelier", "Florist")));

        // Relevance is unchanged: the career naming the category scores full, the rest are damped.
        assertEquals(100.0, allScores.get("Data Scientist"));
        assertEquals(30.0, allScores.get("Florist"));

        // The HTTP DTO stays capped at the top N -- only the event carries the full field.
        assertEquals(AssessmentConstants.TOP_CAREERS_TO_RECOMMEND, dto.getAllCareerScores().size());
        assertTrue(allScores.size() > dto.getAllCareerScores().size());

        // Scalars still agree with the winner.
        assertEquals("Data Scientist", event.getTopCareerPath());
        assertEquals(100.0, event.getCareerMatchPercentage());
    }

    @Test
    @DisplayName("submitAttempt: a partial submission scores against the attempt size, not the answer count")
    void submitAttempt_PartialAnswers_ScoresAgainstAttemptSize() {
        List<Question> questions = fiveQuestions();
        SubmitAnswerRequest request = submitAll(questions, 1);
        // Keep only the first answer: one perfect answer out of five questions.
        request.setAnswers(List.of(request.getAnswers().get(0)));

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(inProgressAttempt()));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.findByCategoryIdOrderByOrderIndex(CATEGORY_ID)).thenReturn(questions);
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(optionsFor(questions));
        when(careerPathRepository.findAll()).thenReturn(List.of());
        when(resultRepository.save(any(AssessmentResult.class))).thenAnswer(inv -> inv.getArgument(0));

        assessmentService.submitAttempt(USER_ID, request);

        ArgumentCaptor<AssessmentResult> saved = ArgumentCaptor.forClass(AssessmentResult.class);
        verify(resultRepository).save(saved.capture());
        // 3 of a possible 15 -- not 100%, which is what an answers-based denominator would give.
        assertEquals(3, saved.getValue().getRawScore());
        assertEquals(15, saved.getValue().getMaxPossibleScore());
        assertEquals(20.0, saved.getValue().getCategoryScorePercentage());
        // Empty career table still records a result, with no top career.
        assertEquals(null, saved.getValue().getTopCareerPathId());
    }

    @Test
    @DisplayName("submitAttempt: answering one question twice is refused, so the score cannot be inflated")
    void submitAttempt_DuplicateQuestionId_ThrowsBadRequest() {
        List<Question> questions = fiveQuestions();
        SubmitAnswerRequest request = submitAll(questions, 1);
        // Four distinct answers plus a repeat of the first: five in total, so this exercises the
        // duplicate check rather than tripping the answer-count cap that precedes it.
        List<AnswerDto> answers = new ArrayList<>(request.getAnswers().subList(0, 4));
        answers.add(answers.get(0));
        request.setAnswers(answers);

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(inProgressAttempt()));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.findByCategoryIdOrderByOrderIndex(CATEGORY_ID)).thenReturn(questions);
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(optionsFor(questions));

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.submitAttempt(USER_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(attemptAnswerRepository, never()).saveAll(anyList());
        verify(resultRepository, never()).save(any(AssessmentResult.class));
    }

    @Test
    @DisplayName("submitAttempt: submitting more answers than the attempt drew is refused")
    void submitAttempt_MoreAnswersThanAllowed_ThrowsBadRequest() {
        // Eight individually-valid answers from the same category. Every one passes the membership
        // check, so without the cap they would score 24 against a denominator of 15 -- 160%.
        List<Question> pool = questions(8);
        SubmitAnswerRequest request = submitAll(pool, 1);

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(inProgressAttempt()));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.findByCategoryIdOrderByOrderIndex(CATEGORY_ID)).thenReturn(pool);
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(optionsFor(pool));

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.submitAttempt(USER_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(attemptAnswerRepository, never()).saveAll(anyList());
        verify(resultRepository, never()).save(any(AssessmentResult.class));
    }

    @Test
    @DisplayName("submitAttempt: an option belonging to another question is refused")
    void submitAttempt_OptionFromDifferentQuestion_ThrowsBadRequest() {
        List<Question> questions = fiveQuestions();
        SubmitAnswerRequest request = submitAll(questions, 1);
        // Point question 1 at question 2's high-weight option.
        request.getAnswers().get(0).setSelectedOptionId(21L);

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(inProgressAttempt()));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(questionRepository.findByCategoryIdOrderByOrderIndex(CATEGORY_ID)).thenReturn(questions);
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(optionsFor(questions));

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.submitAttempt(USER_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(resultRepository, never()).save(any(AssessmentResult.class));
    }

    @Test
    @DisplayName("submitAttempt: resubmitting a completed attempt is refused with 409")
    void submitAttempt_AlreadyCompleted_ThrowsBadRequest() {
        AssessmentAttempt completed = inProgressAttempt();
        completed.setStatus(AttemptStatus.COMPLETED);

        SubmitAnswerRequest request = submitAll(fiveQuestions(), 1);

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(completed));

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.submitAttempt(USER_ID, request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(resultRepository, never()).save(any(AssessmentResult.class));
    }

    @Test
    @DisplayName("submitAttempt: another user's attempt is a 404, not a 403 -- it never confirms the row exists")
    void submitAttempt_OtherUsersAttempt_Throws404() {
        SubmitAnswerRequest request = submitAll(fiveQuestions(), 1);

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, OTHER_USER_ID))
                .thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.submitAttempt(OTHER_USER_ID, request));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(resultRepository, never()).save(any(AssessmentResult.class));
    }

    @Test
    @DisplayName("getResult: returns the stored result with career scores parsed back out of JSON")
    void getResult_ValidAttempt_ReturnsResult() {
        AssessmentResult stored = AssessmentResult.builder()
                .attemptId(ATTEMPT_ID)
                .userId(USER_ID)
                .categoryId(CATEGORY_ID)
                .rawScore(12)
                .maxPossibleScore(15)
                .categoryScorePercentage(80.0)
                .topCareerPathId(1L)
                .careerMatchPercentage(80.0)
                .allCareerScoresJson("{\"Data Scientist\":80.0,\"Chef\":24.0}")
                .build();

        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(inProgressAttempt()));
        when(resultRepository.findByAttemptId(ATTEMPT_ID)).thenReturn(Optional.of(stored));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(careerPathRepository.findById(1L)).thenReturn(Optional.of(
                CareerPath.builder().id(1L).name("Data Scientist").build()));

        AssessmentResultDto dto = assessmentService.getResult(USER_ID, ATTEMPT_ID);

        assertEquals(ATTEMPT_ID, dto.getAttemptId());
        assertEquals("Logical Reasoning", dto.getCategoryName());
        assertEquals(12, dto.getRawScore());
        assertEquals(80.0, dto.getCategoryScorePercentage());
        assertEquals("Data Scientist", dto.getTopCareerPath());
        // Round-trips through Jackson 3 back into a Map.
        assertEquals(80.0, dto.getAllCareerScores().get("Data Scientist"));
        assertEquals(24.0, dto.getAllCareerScores().get("Chef"));
    }

    @Test
    @DisplayName("getResult: an attempt that was never submitted has no result to return")
    void getResult_NotSubmitted_Throws404() {
        when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                .thenReturn(Optional.of(inProgressAttempt()));
        when(resultRepository.findByAttemptId(ATTEMPT_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.getResult(USER_ID, ATTEMPT_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getQuestions: an unknown category is a 404 rather than an empty list")
    void getQuestions_UnknownCategory_Throws404() {
        when(categoryRepository.existsById(CATEGORY_ID)).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class,
                () -> assessmentService.getQuestions(CATEGORY_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(questionRepository, never()).findByCategoryIdOrderByOrderIndex(any());
    }
}
