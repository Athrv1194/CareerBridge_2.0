package com.careerbridge.notification;

import com.careerbridge.notification.constants.NotificationConstants;
import com.careerbridge.notification.dto.NotificationResponse;
import com.careerbridge.notification.dto.UnreadCountResponse;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;
import com.careerbridge.notification.event.SubscriptionActivatedEvent;
import com.careerbridge.notification.exception.CustomException;
import com.careerbridge.notification.model.NotificationDocument;
import com.careerbridge.notification.model.NotificationRecord;
import com.careerbridge.notification.model.UserContact;
import com.careerbridge.notification.repository.NotificationDocumentRepository;
import com.careerbridge.notification.repository.NotificationRecordRepository;
import com.careerbridge.notification.repository.UserContactRepository;
import com.careerbridge.notification.service.EmailService;
import com.careerbridge.notification.service.NotificationServiceImpl;
import com.careerbridge.notification.service.PaymentServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long RECOMMENDATION_ID = 7L;
    private static final Long PAYMENT_ID = 77L;
    private static final String EMAIL = "ada@careerbridge.com";
    private static final String DOC_ID = "6650f1c2a1b2c3d4e5f60718";

    @Mock private NotificationRecordRepository notificationRecordRepository;
    @Mock private NotificationDocumentRepository notificationDocumentRepository;
    @Mock private UserContactRepository userContactRepository;
    @Mock private EmailService emailService;
    @Mock private PaymentServiceClient paymentServiceClient;

    @InjectMocks private NotificationServiceImpl notificationService;

    private RecommendationGeneratedEvent event;
    private SubscriptionActivatedEvent subscriptionEvent;

    @BeforeEach
    void setUp() {
        event = RecommendationGeneratedEvent.builder()
                .userId(USER_ID)
                .recommendationId(RECOMMENDATION_ID)
                .topCareerName("Backend Developer")
                .matchPercentage(66.666)
                .categoryName("System Design")
                .generatedAt(LocalDateTime.now())
                .build();

        subscriptionEvent = SubscriptionActivatedEvent.builder()
                .userId(USER_ID)
                .paymentId(PAYMENT_ID)
                .planName("STUDENT_PREMIUM")
                .amount(new BigDecimal("199.00"))
                .invoiceNumber("CB-INV-000077")
                .userRole("STUDENT")
                .build();
    }

    private UserContact contact() {
        return UserContact.builder()
                .id(1L).userId(USER_ID).email(EMAIL)
                .firstName("Ada").lastName("Lovelace")
                .build();
    }

    private NotificationRecord captureSavedRecord() {
        ArgumentCaptor<NotificationRecord> captor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRecordRepository).save(captor.capture());
        return captor.getValue();
    }

    private NotificationDocument captureSavedDocument() {
        ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
        verify(notificationDocumentRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("recommendation: sends the email and writes both the Mongo feed entry and the audit row")
    void processRecommendationNotification_Success_SendsEmailAndSavesBothRecords() {
        when(notificationRecordRepository.existsByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(false);
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.of(contact()));
        when(notificationDocumentRepository.findByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(Optional.empty());
        when(emailService.sendRecommendationEmail(eq(EMAIL), anyString(), anyString(), anyDouble(), anyLong()))
                .thenReturn(true);

        notificationService.processRecommendationNotification(event);

        NotificationDocument doc = captureSavedDocument();
        assertEquals(USER_ID, doc.getUserId());
        assertEquals(RECOMMENDATION_ID, doc.getRecommendationId());
        assertEquals(NotificationConstants.TYPE_RECOMMENDATION, doc.getNotificationType());
        assertFalse(doc.getIsRead(), "a new notification must be unread, not null");
        // @CreationTimestamp is Hibernate-only and does nothing on a Mongo document, so the
        // service has to set this or the OrderByCreatedAtDesc feed loses its ordering.
        assertNotNull(doc.getCreatedAt(), "createdAt must be set explicitly for Mongo");
        assertNull(doc.getReadAt());
        assertTrue(doc.getMessage().contains("Backend Developer"), doc.getMessage());

        NotificationRecord record = captureSavedRecord();
        assertEquals(NotificationConstants.STATUS_SENT, record.getStatus());
        assertEquals(NotificationConstants.TYPE_EMAIL, record.getNotificationType());
        assertEquals(EMAIL, record.getRecipientEmail());
        assertNull(record.getErrorMessage());
    }

    @Test
    @DisplayName("recommendation: a redelivered event does no work at all -- no email, no writes")
    void processRecommendationNotification_DuplicateRecommendationId_SkipsEverything() {
        when(notificationRecordRepository.existsByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(true);

        notificationService.processRecommendationNotification(event);

        verify(notificationDocumentRepository, never()).save(any(NotificationDocument.class));
        verify(notificationRecordRepository, never()).save(any(NotificationRecord.class));
        verify(emailService, never()).sendRecommendationEmail(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("recommendation: no contact row skips the email but still creates the in-app notification")
    void processRecommendationNotification_NoContactRecord_SkipsEmailButStillCreatesInAppNotification() {
        when(notificationRecordRepository.existsByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(false);
        // Expected state, not an error: this student registered before the service first ran.
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(notificationDocumentRepository.findByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(Optional.empty());

        notificationService.processRecommendationNotification(event);

        verify(emailService, never()).sendRecommendationEmail(any(), any(), any(), any(), any());
        assertNotNull(captureSavedDocument(), "the student must still see it in-app");

        NotificationRecord record = captureSavedRecord();
        assertEquals(NotificationConstants.STATUS_FAILED, record.getStatus());
        assertNull(record.getRecipientEmail());
        assertTrue(record.getErrorMessage().contains("No contact record"), record.getErrorMessage());
    }

    @Test
    @DisplayName("recommendation: an SMTP failure is recorded as FAILED but never costs the in-app notification")
    void processRecommendationNotification_EmailFails_SavesFailedStatusButStillCreatesInAppNotification() {
        when(notificationRecordRepository.existsByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(false);
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.of(contact()));
        when(notificationDocumentRepository.findByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(Optional.empty());
        when(emailService.sendRecommendationEmail(eq(EMAIL), anyString(), anyString(), anyDouble(), anyLong()))
                .thenReturn(false);

        notificationService.processRecommendationNotification(event);

        assertNotNull(captureSavedDocument());

        NotificationRecord record = captureSavedRecord();
        assertEquals(NotificationConstants.STATUS_FAILED, record.getStatus());
        assertEquals(EMAIL, record.getRecipientEmail(), "the attempted address is still recorded");
        assertNotNull(record.getErrorMessage());
    }

    @Test
    @DisplayName("recommendation: an existing feed entry is not duplicated, but the email still goes out")
    void processRecommendationNotification_ExistingDocument_DoesNotCreateDuplicateInAppNotification() {
        when(notificationRecordRepository.existsByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(false);
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.of(contact()));
        // Nothing constrains the Mongo collection the way the unique index constrains Postgres,
        // so this guard is what stops duplicates in the student's feed.
        when(notificationDocumentRepository.findByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(Optional.of(NotificationDocument.builder().id(DOC_ID).build()));
        when(emailService.sendRecommendationEmail(eq(EMAIL), anyString(), anyString(), anyDouble(), anyLong()))
                .thenReturn(true);

        notificationService.processRecommendationNotification(event);

        verify(notificationDocumentRepository, never()).save(any(NotificationDocument.class));
        assertEquals(NotificationConstants.STATUS_SENT, captureSavedRecord().getStatus());
    }

    @Test
    @DisplayName("recommendation: a null matchPercentage renders as n/a instead of throwing NPE")
    void processRecommendationNotification_NullMatchPercentage_DoesNotThrow() {
        // String.format("%.1f", null) throws NPE, and here that NPE would abort the whole handler.
        event.setMatchPercentage(null);

        when(notificationRecordRepository.existsByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(false);
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.of(contact()));
        when(notificationDocumentRepository.findByUserIdAndRecommendationId(USER_ID, RECOMMENDATION_ID))
                .thenReturn(Optional.empty());
        when(emailService.sendRecommendationEmail(eq(EMAIL), anyString(), anyString(), eq(null), anyLong()))
                .thenReturn(true);

        assertDoesNotThrow(() -> notificationService.processRecommendationNotification(event));

        assertTrue(captureSavedDocument().getMessage().contains("n/a"));
    }

    @Test
    @DisplayName("contact: a first registration creates the row")
    void upsertContact_NewUser_CreatesContactRow() {
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        notificationService.upsertContact(StudentRegisteredEvent.builder()
                .userId(USER_ID).email(EMAIL).firstName("Ada").lastName("Lovelace")
                .role("STUDENT").organizationId(7L).registeredAt(LocalDateTime.now())
                .build());

        ArgumentCaptor<UserContact> captor = ArgumentCaptor.forClass(UserContact.class);
        verify(userContactRepository).save(captor.capture());
        assertNull(captor.getValue().getId(), "a new row has no id yet");
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(EMAIL, captor.getValue().getEmail());
    }

    @Test
    @DisplayName("contact: a re-publish updates the existing row in place rather than adding a second")
    void upsertContact_ExistingUser_UpdatesInPlaceWithoutASecondRow() {
        UserContact existing = UserContact.builder()
                .id(99L).userId(USER_ID).email("old@careerbridge.com")
                .firstName("Old").lastName("Name").build();
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        notificationService.upsertContact(StudentRegisteredEvent.builder()
                .userId(USER_ID).email("corrected@careerbridge.com")
                .firstName("Ada").lastName("Lovelace").build());

        ArgumentCaptor<UserContact> captor = ArgumentCaptor.forClass(UserContact.class);
        verify(userContactRepository).save(captor.capture());
        assertEquals(99L, captor.getValue().getId(), "same row, not a new one");
        assertEquals("corrected@careerbridge.com", captor.getValue().getEmail(),
                "a corrected address from auth-service must actually land");
        assertEquals("Ada", captor.getValue().getFirstName());
    }

    @Test
    @DisplayName("my: returns the feed in repository order, newest first")
    void getMyNotifications_ReturnsNewestFirst() {
        NotificationDocument newer = NotificationDocument.builder()
                .id("b").userId(USER_ID).recommendationId(2L).title("t2").message("m2")
                .isRead(false).notificationType(NotificationConstants.TYPE_RECOMMENDATION)
                .createdAt(LocalDateTime.now()).build();
        NotificationDocument older = NotificationDocument.builder()
                .id("a").userId(USER_ID).recommendationId(1L).title("t1").message("m1")
                .isRead(true).notificationType(NotificationConstants.TYPE_RECOMMENDATION)
                .createdAt(LocalDateTime.now().minusDays(1)).readAt(LocalDateTime.now()).build();
        when(notificationDocumentRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(newer, older));

        List<NotificationResponse> feed = notificationService.getMyNotifications(USER_ID);

        assertEquals(2, feed.size());
        assertEquals("b", feed.get(0).getId());
        assertFalse(feed.get(0).getIsRead());
        assertEquals("a", feed.get(1).getId());
        assertTrue(feed.get(1).getIsRead());
        assertNotNull(feed.get(1).getReadAt());
    }

    @Test
    @DisplayName("read: marking an unread notification sets both isRead and readAt")
    void markAsRead_Success_SetsIsReadAndReadAt() {
        NotificationDocument doc = NotificationDocument.builder()
                .id(DOC_ID).userId(USER_ID).recommendationId(RECOMMENDATION_ID)
                .title("t").message("m").isRead(false)
                .notificationType(NotificationConstants.TYPE_RECOMMENDATION)
                .createdAt(LocalDateTime.now()).build();
        when(notificationDocumentRepository.findByIdAndUserId(DOC_ID, USER_ID))
                .thenReturn(Optional.of(doc));
        when(notificationDocumentRepository.save(any(NotificationDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(USER_ID, DOC_ID);

        assertTrue(response.getIsRead());
        assertNotNull(response.getReadAt());
    }

    @Test
    @DisplayName("read: re-reading an already-read notification does not slide readAt forward")
    void markAsRead_AlreadyRead_IsIdempotent() {
        LocalDateTime originalReadAt = LocalDateTime.now().minusDays(3);
        NotificationDocument doc = NotificationDocument.builder()
                .id(DOC_ID).userId(USER_ID).title("t").message("m")
                .isRead(true).readAt(originalReadAt)
                .notificationType(NotificationConstants.TYPE_RECOMMENDATION)
                .createdAt(LocalDateTime.now().minusDays(4)).build();
        when(notificationDocumentRepository.findByIdAndUserId(DOC_ID, USER_ID))
                .thenReturn(Optional.of(doc));

        NotificationResponse response = notificationService.markAsRead(USER_ID, DOC_ID);

        verify(notificationDocumentRepository, never()).save(any(NotificationDocument.class));
        assertEquals(originalReadAt, response.getReadAt());
    }

    @Test
    @DisplayName("read: another student's notification is not found rather than returned")
    void markAsRead_BelongsToAnotherUser_Throws404() {
        // Ownership is part of the query, so a foreign document never loads at all.
        when(notificationDocumentRepository.findByIdAndUserId(DOC_ID, USER_ID))
                .thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.markAsRead(USER_ID, DOC_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("unread-count: passes the repository count straight through")
    void getUnreadCount_ReturnsRepositoryCount() {
        when(notificationDocumentRepository.countByUserIdAndIsReadFalse(USER_ID)).thenReturn(7L);

        UnreadCountResponse response = notificationService.getUnreadCount(USER_ID);

        assertEquals(USER_ID, response.getUserId());
        assertEquals(7L, response.getUnreadCount());
    }

    @Test
    @DisplayName("invoice: with a contact and a successful PDF fetch, sends the email with the attachment and writes the in-app notification")
    void processSubscriptionInvoice_Success_SendsEmailWithAttachmentAndSavesInAppNotification() {
        byte[] pdf = {'%', 'P', 'D', 'F'};
        when(notificationDocumentRepository.findByUserIdAndPaymentId(USER_ID, PAYMENT_ID))
                .thenReturn(Optional.empty());
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.of(contact()));
        when(paymentServiceClient.fetchInvoicePdf(PAYMENT_ID, USER_ID, "STUDENT")).thenReturn(pdf);
        when(emailService.sendInvoiceEmail(eq(EMAIL), anyString(), any(), anyString(), eq(pdf), eq(PAYMENT_ID)))
                .thenReturn(true);

        notificationService.processSubscriptionInvoice(subscriptionEvent);

        NotificationDocument doc = captureSavedDocument();
        assertEquals(USER_ID, doc.getUserId());
        assertEquals(PAYMENT_ID, doc.getPaymentId());
        assertNull(doc.getRecommendationId(), "a subscription notification is not tied to a recommendation");
        assertEquals(NotificationConstants.TYPE_SUBSCRIPTION, doc.getNotificationType());
        assertNotNull(doc.getCreatedAt());

        verify(emailService).sendInvoiceEmail(eq(EMAIL), anyString(), any(), anyString(), eq(pdf), eq(PAYMENT_ID));
        // No Postgres audit row for this event type -- see the class comment on why.
        verify(notificationRecordRepository, never()).save(any(NotificationRecord.class));
    }

    @Test
    @DisplayName("invoice: a redelivered event does no work at all")
    void processSubscriptionInvoice_AlreadyProcessed_SkipsEverything() {
        when(notificationDocumentRepository.findByUserIdAndPaymentId(USER_ID, PAYMENT_ID))
                .thenReturn(Optional.of(NotificationDocument.builder().id(DOC_ID).build()));

        notificationService.processSubscriptionInvoice(subscriptionEvent);

        verify(notificationDocumentRepository, never()).save(any(NotificationDocument.class));
        verify(paymentServiceClient, never()).fetchInvoicePdf(any(), any(), any());
        verify(emailService, never()).sendInvoiceEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("invoice: no contact row skips the email but still creates the in-app notification")
    void processSubscriptionInvoice_NoContactRecord_SkipsEmailButStillCreatesInAppNotification() {
        when(notificationDocumentRepository.findByUserIdAndPaymentId(USER_ID, PAYMENT_ID))
                .thenReturn(Optional.empty());
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(paymentServiceClient.fetchInvoicePdf(PAYMENT_ID, USER_ID, "STUDENT")).thenReturn(new byte[]{1});

        notificationService.processSubscriptionInvoice(subscriptionEvent);

        assertNotNull(captureSavedDocument(), "the student must still see it in-app");
        verify(emailService, never()).sendInvoiceEmail(any(), any(), any(), any(), any(), any());
    }

    /**
     * A payment-service outage must not stop the confirmation email -- the user was genuinely
     * charged, and the in-app notification and email both still happen, just without the PDF.
     */
    @Test
    @DisplayName("invoice: a failed PDF fetch still sends the email, without an attachment")
    void processSubscriptionInvoice_PdfFetchFails_StillSendsEmailWithoutAttachment() {
        when(notificationDocumentRepository.findByUserIdAndPaymentId(USER_ID, PAYMENT_ID))
                .thenReturn(Optional.empty());
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.of(contact()));
        when(paymentServiceClient.fetchInvoicePdf(PAYMENT_ID, USER_ID, "STUDENT")).thenReturn(null);
        when(emailService.sendInvoiceEmail(eq(EMAIL), anyString(), any(), anyString(), eq(null), eq(PAYMENT_ID)))
                .thenReturn(true);

        assertDoesNotThrow(() -> notificationService.processSubscriptionInvoice(subscriptionEvent));

        verify(emailService).sendInvoiceEmail(eq(EMAIL), anyString(), any(), anyString(), eq(null), eq(PAYMENT_ID));
    }

    @Test
    @DisplayName("invoice: an SMTP failure does not throw and still leaves the in-app notification written")
    void processSubscriptionInvoice_EmailFails_DoesNotThrow() {
        when(notificationDocumentRepository.findByUserIdAndPaymentId(USER_ID, PAYMENT_ID))
                .thenReturn(Optional.empty());
        when(userContactRepository.findByUserId(USER_ID)).thenReturn(Optional.of(contact()));
        when(paymentServiceClient.fetchInvoicePdf(PAYMENT_ID, USER_ID, "STUDENT")).thenReturn(new byte[]{1});
        when(emailService.sendInvoiceEmail(any(), any(), any(), any(), any(), any())).thenReturn(false);

        assertDoesNotThrow(() -> notificationService.processSubscriptionInvoice(subscriptionEvent));

        assertNotNull(captureSavedDocument());
    }
}
