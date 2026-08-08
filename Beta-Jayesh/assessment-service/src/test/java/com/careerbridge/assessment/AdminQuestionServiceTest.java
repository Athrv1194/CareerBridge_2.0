package com.careerbridge.assessment;

import com.careerbridge.assessment.constants.AssessmentConstants;
import com.careerbridge.assessment.dto.AdminOptionRequest;
import com.careerbridge.assessment.dto.AdminQuestionRequest;
import com.careerbridge.assessment.dto.AdminQuestionResponse;
import com.careerbridge.assessment.exception.CustomException;
import com.careerbridge.assessment.model.Category;
import com.careerbridge.assessment.model.Option;
import com.careerbridge.assessment.model.Question;
import com.careerbridge.assessment.repository.CategoryRepository;
import com.careerbridge.assessment.repository.OptionRepository;
import com.careerbridge.assessment.repository.QuestionRepository;
import com.careerbridge.assessment.service.AdminQuestionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito -- no Spring context, no database. Matches AssessmentServiceTest's shape.
 *
 * The weight tests are the point of this class: the exactly-one-max-weight rule is what keeps the
 * graded 0..3 scoring model coherent, and nothing else enforces it.
 */
@ExtendWith(MockitoExtension.class)
class AdminQuestionServiceTest {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ORG_ADMIN = "ORG_ADMIN";
    private static final String STUDENT = "STUDENT";

    private static final Long CATEGORY_ID = 1L;
    private static final Long QUESTION_ID = 10L;

    @Mock private QuestionRepository questionRepository;
    @Mock private OptionRepository optionRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks private AdminQuestionServiceImpl adminQuestionService;

    private static Category category() {
        return Category.builder().id(CATEGORY_ID).name("Programming Fundamentals").build();
    }

    private static Question question(Long id, Long categoryId, boolean active) {
        return Question.builder()
                .id(id)
                .categoryId(categoryId)
                .questionText("What is a closure?")
                .orderIndex(1)
                .isActive(active)
                .build();
    }

    private static Option option(Long id, Long questionId, int weight, int orderIndex) {
        return Option.builder()
                .id(id)
                .questionId(questionId)
                .optionText("Option " + orderIndex)
                .weight(weight)
                .orderIndex(orderIndex)
                .build();
    }

    /** A valid request: exactly one option at MAX_OPTION_WEIGHT. */
    private static AdminQuestionRequest request() {
        return AdminQuestionRequest.builder()
                .text("What is a closure?")
                .categoryId(CATEGORY_ID)
                .orderIndex(1)
                .isActive(true)
                .options(List.of(
                        AdminOptionRequest.builder().text("Correct").weight(3).build(),
                        AdminOptionRequest.builder().text("Partial").weight(1).build(),
                        AdminOptionRequest.builder().text("Wrong").weight(0).build()))
                .build();
    }

    private static AdminQuestionRequest requestWithWeights(Integer... weights) {
        List<AdminOptionRequest> options = new java.util.ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            options.add(AdminOptionRequest.builder()
                    .text("Option " + (i + 1)).weight(weights[i]).build());
        }
        AdminQuestionRequest req = request();
        req.setOptions(options);
        return req;
    }

    // -------------------------------------------------------------------------------------------
    // addQuestion
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("addQuestion: saves the question and its options, and returns them")
    void addQuestion_Success() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(questionRepository.save(any(Question.class)))
                .thenReturn(question(QUESTION_ID, CATEGORY_ID, true));
        when(optionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        AdminQuestionResponse response = adminQuestionService.addQuestion(SUPER_ADMIN, request());

        assertEquals(QUESTION_ID, response.getId());
        assertEquals("Programming Fundamentals", response.getCategoryName());
        assertEquals(3, response.getOptions().size());
        assertTrue(response.getIsActive());

        // orderIndex is assigned from list position, so the stored order matches what was submitted.
        ArgumentCaptor<List<Option>> saved = ArgumentCaptor.forClass(List.class);
        verify(optionRepository).saveAll(saved.capture());
        assertEquals(1, saved.getValue().get(0).getOrderIndex());
        assertEquals(3, saved.getValue().get(0).getWeight());
    }

    @Test
    @DisplayName("addQuestion: an unknown category is a 404 and nothing is saved")
    void addQuestion_CategoryNotFound() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.addQuestion(SUPER_ADMIN, request()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertTrue(ex.getMessage().contains("Category not found"));
        verify(questionRepository, never()).save(any());
    }

    @Test
    @DisplayName("addQuestion: no option at the maximum weight is a 400")
    void addQuestion_ZeroMaxWeightOptions() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

        // Every option below MAX_OPTION_WEIGHT: the question would be unanswerable at full marks and
        // would quietly lower the ceiling of every attempt that drew it.
        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.addQuestion(SUPER_ADMIN, requestWithWeights(2, 1, 0)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Exactly one option must have the maximum weight"));
        verify(questionRepository, never()).save(any());
    }

    @Test
    @DisplayName("addQuestion: two options at the maximum weight is a 400")
    void addQuestion_TwoMaxWeightOptions() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.addQuestion(SUPER_ADMIN, requestWithWeights(3, 3, 0)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains(String.valueOf(AssessmentConstants.MAX_OPTION_WEIGHT)));
        verify(questionRepository, never()).save(any());
    }

    @Test
    @DisplayName("addQuestion: a STUDENT is refused before any repository call")
    void addQuestion_UnauthorizedRole() {
        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.addQuestion(STUDENT, request()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        // 403 before the category lookup: a rejected caller should not even cause a query.
        verify(categoryRepository, never()).findById(anyLong());
        verify(questionRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------------------------
    // editQuestion
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("editQuestion: replaces the whole option set, deleting the old rows first")
    void editQuestion_Success() {
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, true)));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(questionRepository.save(any(Question.class))).thenAnswer(i -> i.getArgument(0));
        when(optionRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        AdminQuestionResponse response = adminQuestionService.editQuestion(
                ORG_ADMIN, QUESTION_ID, request());

        assertEquals(3, response.getOptions().size());
        // Delete must happen -- Question holds no @OneToMany to Option, so there is no cascade and
        // the old rows would otherwise survive alongside the new ones.
        verify(optionRepository).deleteByQuestionId(QUESTION_ID);
        verify(optionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("editQuestion: an unknown question is a 404")
    void editQuestion_QuestionNotFound() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.editQuestion(SUPER_ADMIN, QUESTION_ID, request()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertTrue(ex.getMessage().contains("Question not found"));
        verify(optionRepository, never()).deleteByQuestionId(anyLong());
    }

    @Test
    @DisplayName("editQuestion: an unknown category is a 404 and no options are touched")
    void editQuestion_CategoryNotFound() {
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, true)));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.editQuestion(SUPER_ADMIN, QUESTION_ID, request()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(optionRepository, never()).deleteByQuestionId(anyLong());
    }

    @Test
    @DisplayName("editQuestion: a max-weight violation is a 400 and the old options survive")
    void editQuestion_MaxWeightViolation() {
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, true)));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.editQuestion(
                        SUPER_ADMIN, QUESTION_ID, requestWithWeights(3, 3)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        // The guard runs before the delete, so a rejected edit cannot leave a question with no
        // options at all.
        verify(optionRepository, never()).deleteByQuestionId(anyLong());
        verify(optionRepository, never()).saveAll(anyList());
    }

    // -------------------------------------------------------------------------------------------
    // getQuestion
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getQuestion: returns the question with its options")
    void getQuestion_Success() {
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, true)));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(optionRepository.findByQuestionIdOrderByOrderIndex(QUESTION_ID))
                .thenReturn(List.of(option(1L, QUESTION_ID, 3, 1), option(2L, QUESTION_ID, 0, 2)));

        AdminQuestionResponse response = adminQuestionService.getQuestion(SUPER_ADMIN, QUESTION_ID);

        assertEquals(QUESTION_ID, response.getId());
        assertEquals(2, response.getOptions().size());
        // The admin view exposes weight; the student-facing OptionDto never does.
        assertEquals(3, response.getOptions().get(0).getWeight());
    }

    @Test
    @DisplayName("getQuestion: an inactive question is still returned to an admin")
    void getQuestion_InactiveQuestion_StillReturns() {
        // requireQuestion uses the plain findById precisely so this works -- an admin who cannot see
        // a retired question cannot reactivate it.
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, false)));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(optionRepository.findByQuestionIdOrderByOrderIndex(QUESTION_ID)).thenReturn(List.of());

        AdminQuestionResponse response = adminQuestionService.getQuestion(ORG_ADMIN, QUESTION_ID);

        assertFalse(response.getIsActive());
    }

    @Test
    @DisplayName("getQuestion: an unknown id is a 404")
    void getQuestion_NotFound() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.getQuestion(SUPER_ADMIN, QUESTION_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // -------------------------------------------------------------------------------------------
    // listQuestions
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("listQuestions: with no filter, returns every question including inactive ones")
    void listQuestions_AllReturned() {
        when(questionRepository.findAllByOrderByCategoryIdAscOrderIndexAsc())
                .thenReturn(List.of(question(10L, 1L, true), question(11L, 2L, false)));
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(), Category.builder().id(2L).name("Database & SQL").build()));
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList()))
                .thenReturn(List.of(option(1L, 10L, 3, 1)));

        List<AdminQuestionResponse> result = adminQuestionService.listQuestions(SUPER_ADMIN, null);

        assertEquals(2, result.size());
        // The inactive one must be present -- this is the only query in the service that ignores
        // isActive.
        assertFalse(result.get(1).getIsActive());
        assertEquals("Database & SQL", result.get(1).getCategoryName());
        // A question with no options maps to an empty list rather than throwing.
        assertTrue(result.get(1).getOptions().isEmpty());
    }

    @Test
    @DisplayName("listQuestions: a categoryId narrows the result to that category")
    void listQuestions_FilteredByCategory() {
        when(questionRepository.findAllByOrderByCategoryIdAscOrderIndexAsc())
                .thenReturn(List.of(question(10L, 1L, true), question(11L, 2L, true)));
        when(categoryRepository.findAll()).thenReturn(List.of(category()));
        when(optionRepository.findByQuestionIdInOrderByOrderIndex(anyList())).thenReturn(List.of());

        List<AdminQuestionResponse> result = adminQuestionService.listQuestions(ORG_ADMIN, 1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    @DisplayName("listQuestions: an empty bank returns an empty list, not an error")
    void listQuestions_EmptyBank_ReturnsEmptyList() {
        when(questionRepository.findAllByOrderByCategoryIdAscOrderIndexAsc()).thenReturn(List.of());

        List<AdminQuestionResponse> result = adminQuestionService.listQuestions(SUPER_ADMIN, null);

        assertTrue(result.isEmpty());
        // Short-circuits before the category and option queries -- no point loading either.
        verify(categoryRepository, never()).findAll();
        verify(optionRepository, never()).findByQuestionIdInOrderByOrderIndex(anyList());
    }

    @Test
    @DisplayName("listQuestions: a STUDENT is refused")
    void listQuestions_UnauthorizedRole() {
        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.listQuestions(STUDENT, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(questionRepository, never()).findAllByOrderByCategoryIdAscOrderIndexAsc();
    }

    // -------------------------------------------------------------------------------------------
    // activateQuestion / deactivateQuestion
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("activateQuestion: sets isActive true and saves")
    void activateQuestion_Success() {
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, false)));

        adminQuestionService.activateQuestion(SUPER_ADMIN, QUESTION_ID);

        ArgumentCaptor<Question> saved = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(saved.capture());
        assertTrue(saved.getValue().getIsActive());
    }

    @Test
    @DisplayName("activateQuestion: an already-active question is a 400")
    void activateQuestion_AlreadyActive() {
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, true)));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.activateQuestion(SUPER_ADMIN, QUESTION_ID));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(questionRepository, never()).save(any());
    }

    @Test
    @DisplayName("activateQuestion: an unknown id is a 404")
    void activateQuestion_NotFound() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.activateQuestion(SUPER_ADMIN, QUESTION_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("deactivateQuestion: sets isActive false and saves, never hard-deleting")
    void deactivateQuestion_Success() {
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, true)));
        when(questionRepository.save(any(Question.class))).thenAnswer(i -> i.getArgument(0));
        when(questionRepository.countByCategoryIdAndIsActiveTrue(CATEGORY_ID)).thenReturn(8);

        adminQuestionService.deactivateQuestion(SUPER_ADMIN, QUESTION_ID);

        ArgumentCaptor<Question> saved = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(saved.capture());
        assertFalse(saved.getValue().getIsActive());
        // Soft retire: historical AttemptAnswer rows must keep pointing at a real question.
        verify(questionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deactivateQuestion: dropping the category below the minimum still succeeds")
    void deactivateQuestion_BelowThreshold_StillSucceedsAndChecksCount() {
        // Deliberately NOT blocked: refusing here would trap an admin who has to retire a wrong
        // question before writing its replacement. startAttempt already fails cleanly with "too few
        // questions", and the service logs a WARN naming the category.
        //
        // Asserted through the count query rather than the log line: this project has no
        // log-appender assertion pattern anywhere, and adding a Logback ListAppender for one test
        // would couple the suite to the logging backend.
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, true)));
        when(questionRepository.save(any(Question.class))).thenAnswer(i -> i.getArgument(0));
        when(questionRepository.countByCategoryIdAndIsActiveTrue(CATEGORY_ID))
                .thenReturn(AssessmentConstants.MIN_QUESTIONS_PER_CATEGORY - 1);

        adminQuestionService.deactivateQuestion(SUPER_ADMIN, QUESTION_ID);

        ArgumentCaptor<Question> saved = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(saved.capture());
        assertFalse(saved.getValue().getIsActive());
        verify(questionRepository).countByCategoryIdAndIsActiveTrue(CATEGORY_ID);
    }

    @Test
    @DisplayName("deactivateQuestion: an already-inactive question is a 400")
    void deactivateQuestion_AlreadyInactive() {
        when(questionRepository.findById(QUESTION_ID))
                .thenReturn(Optional.of(question(QUESTION_ID, CATEGORY_ID, false)));

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.deactivateQuestion(SUPER_ADMIN, QUESTION_ID));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(questionRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateQuestion: an unknown id is a 404")
    void deactivateQuestion_NotFound() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> adminQuestionService.deactivateQuestion(SUPER_ADMIN, QUESTION_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
