package com.careerbridge.student;

import com.careerbridge.student.consumer.StudentEventConsumer;
import com.careerbridge.student.event.ResumeGeneratedEvent;
import com.careerbridge.student.event.StudentRegisteredEvent;
import com.careerbridge.student.model.StudentProfile;
import com.careerbridge.student.repository.StudentProfileRepository;
import com.careerbridge.student.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The @RabbitListener annotation is irrelevant to a direct method call, so these drive the
 * listener method itself -- no embedded broker, no @SpringBootTest.
 */
@ExtendWith(MockitoExtension.class)
class StudentEventConsumerTest {

    private static final Long USER_ID = 42L;

    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private StudentService studentService;

    @InjectMocks private StudentEventConsumer consumer;

    private StudentRegisteredEvent event;

    @BeforeEach
    void setUp() {
        event = StudentRegisteredEvent.builder()
                .userId(USER_ID)
                .email("ada@careerbridge.com")
                .firstName("Ada")
                .lastName("Lovelace")
                .role("STUDENT")
                .organizationId(7L)
                .registeredAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("registration event: creates an empty profile carrying the identity from auth-service")
    void studentRegisteredEvent_CreatesEmptyProfile() {
        when(studentProfileRepository.existsByUserId(USER_ID)).thenReturn(false);

        consumer.onStudentRegistered(event);

        ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(saved.capture());
        assertEquals(USER_ID, saved.getValue().getUserId());
        assertEquals("ada@careerbridge.com", saved.getValue().getEmail());
        assertEquals("Ada", saved.getValue().getFirstName());
        assertEquals("Lovelace", saved.getValue().getLastName());
        assertEquals(0, saved.getValue().getProfileCompletionPercentage());
    }

    @Test
    @DisplayName("registration event: a redelivered event does not create a second profile")
    void studentRegisteredEvent_DuplicateEvent_Idempotent() {
        when(studentProfileRepository.existsByUserId(USER_ID)).thenReturn(true);

        consumer.onStudentRegistered(event);

        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("registration event: a failed save is swallowed, so the listener cannot spin on redelivery")
    void studentRegisteredEvent_SaveFails_DoesNotRethrow() {
        when(studentProfileRepository.existsByUserId(USER_ID)).thenReturn(false);
        doThrow(new RuntimeException("db down")).when(studentProfileRepository).save(any(StudentProfile.class));

        assertDoesNotThrow(() -> consumer.onStudentRegistered(event));
    }

    @Test
    @DisplayName("registration event: a payload with no userId is ignored rather than throwing")
    void studentRegisteredEvent_MissingUserId_Ignored() {
        StudentRegisteredEvent malformed = StudentRegisteredEvent.builder().email("x@y.com").build();

        assertDoesNotThrow(() -> consumer.onStudentRegistered(malformed));

        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    // -------------------------------------------------------------------------------------------
    // onResumeGenerated
    // -------------------------------------------------------------------------------------------

    private ResumeGeneratedEvent resumeEvent() {
        return ResumeGeneratedEvent.builder()
                .resumeId(500L)
                .studentId(USER_ID)
                .atsScore(72.5)
                .version(2)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("resume.generated: sets resumeUrl built from the event's resumeId")
    void resumeGeneratedEvent_SetsResumeUrl() {
        consumer.onResumeGenerated(resumeEvent());

        verify(studentService).updateResumeUrl(USER_ID, "/api/resume/download/500");
    }

    @Test
    @DisplayName("resume.generated: routes through StudentService, never the repository directly")
    void resumeGeneratedEvent_DoesNotTouchRepositoryDirectly() {
        consumer.onResumeGenerated(resumeEvent());

        // The write needs recalculate()'s private path in StudentServiceImpl, not a bare field
        // set -- touching the repository here would desync profileCompletionPercentage silently.
        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("resume.generated: a payload with no studentId is ignored rather than throwing")
    void resumeGeneratedEvent_MissingStudentId_Ignored() {
        ResumeGeneratedEvent malformed = ResumeGeneratedEvent.builder().resumeId(500L).build();

        assertDoesNotThrow(() -> consumer.onResumeGenerated(malformed));

        verify(studentService, never()).updateResumeUrl(any(), any());
    }

    @Test
    @DisplayName("resume.generated: a payload with no resumeId is ignored rather than throwing")
    void resumeGeneratedEvent_MissingResumeId_Ignored() {
        ResumeGeneratedEvent malformed = ResumeGeneratedEvent.builder().studentId(USER_ID).build();

        assertDoesNotThrow(() -> consumer.onResumeGenerated(malformed));

        verify(studentService, never()).updateResumeUrl(any(), any());
    }

    @Test
    @DisplayName("resume.generated: a failed update is swallowed, so the listener cannot spin on redelivery")
    void resumeGeneratedEvent_UpdateFails_DoesNotRethrow() {
        doThrow(new RuntimeException("db down"))
                .when(studentService).updateResumeUrl(any(), any());

        assertDoesNotThrow(() -> consumer.onResumeGenerated(resumeEvent()));
    }
}
