package com.careerbridge.notification;

import com.careerbridge.notification.consumer.NotificationEventConsumer;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;
import com.careerbridge.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The @RabbitListener annotations are irrelevant to a direct method call, so these drive the two
 * listener methods themselves -- no embedded broker, no @SpringBootTest.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    private static final Long USER_ID = 42L;
    private static final Long RECOMMENDATION_ID = 7L;

    @Mock private NotificationService notificationService;

    @InjectMocks private NotificationEventConsumer consumer;

    private RecommendationGeneratedEvent recommendationEvent;
    private StudentRegisteredEvent studentEvent;

    @BeforeEach
    void setUp() {
        recommendationEvent = RecommendationGeneratedEvent.builder()
                .userId(USER_ID)
                .recommendationId(RECOMMENDATION_ID)
                .topCareerName("Backend Developer")
                .matchPercentage(66.666)
                .categoryName("System Design")
                .generatedAt(LocalDateTime.now())
                .build();

        studentEvent = StudentRegisteredEvent.builder()
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
    @DisplayName("recommendation.generated: delegates to the service")
    void recommendationGeneratedEvent_DelegatesToTheService() {
        consumer.onRecommendationGenerated(recommendationEvent);

        verify(notificationService).processRecommendationNotification(recommendationEvent);
    }

    @Test
    @DisplayName("recommendation.generated: a payload with no userId is ignored rather than throwing")
    void recommendationGeneratedEvent_MissingUserId_Ignored() {
        RecommendationGeneratedEvent malformed = RecommendationGeneratedEvent.builder()
                .recommendationId(RECOMMENDATION_ID).build();

        assertDoesNotThrow(() -> consumer.onRecommendationGenerated(malformed));

        verify(notificationService, never()).processRecommendationNotification(any());
    }

    @Test
    @DisplayName("recommendation.generated: a payload with no recommendationId is ignored -- it is half the idempotency key")
    void recommendationGeneratedEvent_MissingRecommendationId_Ignored() {
        RecommendationGeneratedEvent malformed = RecommendationGeneratedEvent.builder()
                .userId(USER_ID).build();

        assertDoesNotThrow(() -> consumer.onRecommendationGenerated(malformed));

        verify(notificationService, never()).processRecommendationNotification(any());
    }

    @Test
    @DisplayName("recommendation.generated: a failing service is swallowed, so the listener cannot spin on redelivery")
    void recommendationGeneratedEvent_ServiceThrows_DoesNotRethrow() {
        doThrow(new RuntimeException("mongo down"))
                .when(notificationService).processRecommendationNotification(any());

        assertDoesNotThrow(() -> consumer.onRecommendationGenerated(recommendationEvent));
    }

    @Test
    @DisplayName("student.registered: upserts the contact record and creates no notification")
    void studentRegisteredEvent_UpsertsContact() {
        consumer.onStudentRegistered(studentEvent);

        verify(notificationService).upsertContact(studentEvent);
        // Registering is not something the student needs telling about.
        verify(notificationService, never()).processRecommendationNotification(any());
    }

    @Test
    @DisplayName("student.registered: a payload with no email is ignored -- UserContact.email is NOT NULL")
    void studentRegisteredEvent_MissingEmail_Ignored() {
        StudentRegisteredEvent malformed = StudentRegisteredEvent.builder()
                .userId(USER_ID).firstName("Ada").build();

        assertDoesNotThrow(() -> consumer.onStudentRegistered(malformed));

        verify(notificationService, never()).upsertContact(any());
    }

    @Test
    @DisplayName("student.registered: a failing service is swallowed too")
    void studentRegisteredEvent_ServiceThrows_DoesNotRethrow() {
        doThrow(new RuntimeException("db down")).when(notificationService).upsertContact(any());

        assertDoesNotThrow(() -> consumer.onStudentRegistered(studentEvent));
    }
}
