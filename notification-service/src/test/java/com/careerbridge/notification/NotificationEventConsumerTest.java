package com.careerbridge.notification;

import com.careerbridge.notification.consumer.NotificationEventConsumer;
import com.careerbridge.notification.event.PasswordChangedEvent;
import com.careerbridge.notification.event.PasswordResetRequestedEvent;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;
import com.careerbridge.notification.event.SubscriptionActivatedEvent;
import com.careerbridge.notification.service.EmailService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
    private static final Long PAYMENT_ID = 77L;

    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    @InjectMocks private NotificationEventConsumer consumer;

    private RecommendationGeneratedEvent recommendationEvent;
    private StudentRegisteredEvent studentEvent;
    private SubscriptionActivatedEvent subscriptionEvent;

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

        subscriptionEvent = SubscriptionActivatedEvent.builder()
                .userId(USER_ID)
                .paymentId(PAYMENT_ID)
                .planName("STUDENT_PREMIUM")
                .invoiceNumber("CB-INV-000077")
                .userRole("STUDENT")
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

    @Test
    @DisplayName("password.reset.requested: delegates straight to EmailService, not NotificationService")
    void passwordResetRequestedEvent_DelegatesToEmailService() {
        PasswordResetRequestedEvent event = PasswordResetRequestedEvent.builder()
                .email("ada@careerbridge.com").firstName("Ada").otp("1234").expiresInMinutes(10)
                .build();

        consumer.onPasswordResetRequested(event);

        verify(emailService).sendPasswordResetOtpEmail("ada@careerbridge.com", "Ada", "1234", 10);
        verify(notificationService, never()).processRecommendationNotification(any());
    }

    @Test
    @DisplayName("password.reset.requested: a payload with no otp is ignored")
    void passwordResetRequestedEvent_MissingOtp_Ignored() {
        PasswordResetRequestedEvent malformed = PasswordResetRequestedEvent.builder()
                .email("ada@careerbridge.com").build();

        assertDoesNotThrow(() -> consumer.onPasswordResetRequested(malformed));

        verify(emailService, never()).sendPasswordResetOtpEmail(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("password.reset.requested: a failing EmailService is swallowed rather than spinning the listener")
    void passwordResetRequestedEvent_EmailServiceThrows_DoesNotRethrow() {
        PasswordResetRequestedEvent event = PasswordResetRequestedEvent.builder()
                .email("ada@careerbridge.com").otp("1234").expiresInMinutes(10).build();
        doThrow(new RuntimeException("smtp down")).when(emailService)
                .sendPasswordResetOtpEmail(anyString(), any(), anyString(), anyInt());

        assertDoesNotThrow(() -> consumer.onPasswordResetRequested(event));
    }

    @Test
    @DisplayName("password.changed: delegates straight to EmailService")
    void passwordChangedEvent_DelegatesToEmailService() {
        PasswordChangedEvent event = PasswordChangedEvent.builder()
                .email("ada@careerbridge.com").firstName("Ada").changedAt(LocalDateTime.now())
                .build();

        consumer.onPasswordChanged(event);

        verify(emailService).sendPasswordChangedEmail("ada@careerbridge.com", "Ada");
    }

    @Test
    @DisplayName("password.changed: a payload with no email is ignored")
    void passwordChangedEvent_MissingEmail_Ignored() {
        PasswordChangedEvent malformed = PasswordChangedEvent.builder()
                .firstName("Ada").changedAt(LocalDateTime.now()).build();

        assertDoesNotThrow(() -> consumer.onPasswordChanged(malformed));

        verify(emailService, never()).sendPasswordChangedEmail(anyString(), any());
    }

    @Test
    @DisplayName("subscription.activated: delegates to the service")
    void subscriptionActivatedEvent_DelegatesToTheService() {
        consumer.onSubscriptionActivated(subscriptionEvent);

        verify(notificationService).processSubscriptionInvoice(subscriptionEvent);
    }

    @Test
    @DisplayName("subscription.activated: a payload with no userId is ignored rather than throwing")
    void subscriptionActivatedEvent_MissingUserId_Ignored() {
        SubscriptionActivatedEvent malformed = SubscriptionActivatedEvent.builder()
                .paymentId(PAYMENT_ID).build();

        assertDoesNotThrow(() -> consumer.onSubscriptionActivated(malformed));

        verify(notificationService, never()).processSubscriptionInvoice(any());
    }

    @Test
    @DisplayName("subscription.activated: a payload with no paymentId is ignored -- it is the key for the invoice fetch")
    void subscriptionActivatedEvent_MissingPaymentId_Ignored() {
        SubscriptionActivatedEvent malformed = SubscriptionActivatedEvent.builder()
                .userId(USER_ID).build();

        assertDoesNotThrow(() -> consumer.onSubscriptionActivated(malformed));

        verify(notificationService, never()).processSubscriptionInvoice(any());
    }

    @Test
    @DisplayName("subscription.activated: a failing service is swallowed, so the listener cannot spin on redelivery")
    void subscriptionActivatedEvent_ServiceThrows_DoesNotRethrow() {
        doThrow(new RuntimeException("smtp down"))
                .when(notificationService).processSubscriptionInvoice(any());

        assertDoesNotThrow(() -> consumer.onSubscriptionActivated(subscriptionEvent));
    }
}
