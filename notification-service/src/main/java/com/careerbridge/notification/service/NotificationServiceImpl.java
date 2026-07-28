package com.careerbridge.notification.service;

import com.careerbridge.notification.constants.NotificationConstants;
import com.careerbridge.notification.dto.NotificationResponse;
import com.careerbridge.notification.dto.UnreadCountResponse;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;
import com.careerbridge.notification.exception.CustomException;
import com.careerbridge.notification.model.NotificationDocument;
import com.careerbridge.notification.model.NotificationRecord;
import com.careerbridge.notification.model.UserContact;
import com.careerbridge.notification.repository.NotificationDocumentRepository;
import com.careerbridge.notification.repository.NotificationRecordRepository;
import com.careerbridge.notification.repository.UserContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * No method here is @Transactional, and that is deliberate rather than an omission.
 *
 * Boot auto-configures a JpaTransactionManager only, so @Transactional would cover neither the
 * MongoDB write nor the SMTP call -- it would simply hold a pooled JDBC connection open across a
 * multi-second SMTP round trip while guaranteeing nothing about the two writes it cannot see. The
 * two JPA writes are single statements, already transactional inside SimpleJpaRepository.save.
 * Consistency is achieved by write ORDER instead (see processRecommendationNotification), not by
 * a transaction. Please do not "restore the convention" here.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRecordRepository notificationRecordRepository;
    private final NotificationDocumentRepository notificationDocumentRepository;
    private final UserContactRepository userContactRepository;
    private final EmailService emailService;

    public NotificationServiceImpl(NotificationRecordRepository notificationRecordRepository,
                                   NotificationDocumentRepository notificationDocumentRepository,
                                   UserContactRepository userContactRepository,
                                   EmailService emailService) {
        this.notificationRecordRepository = notificationRecordRepository;
        this.notificationDocumentRepository = notificationDocumentRepository;
        this.userContactRepository = userContactRepository;
        this.emailService = emailService;
    }

    /**
     * Order is load-bearing: Mongo first, email second, the Postgres audit row last.
     *
     * The state that must never occur is "record says SENT, no in-app notification, and the unique
     * constraint now blocks every retry" -- the student would silently never see their result. In
     * this order a Mongo outage aborts before anything irreversible happens and redelivery retries
     * cleanly; a failure after the email costs at worst one duplicate email, while step 3 still
     * prevents a duplicate in the feed. Writing the audit row first would make that bad state the
     * guaranteed outcome of any Mongo hiccup.
     */
    @Override
    public void processRecommendationNotification(RecommendationGeneratedEvent event) {
        Long userId = event.getUserId();
        Long recommendationId = event.getRecommendationId();

        // 1. Fast-path idempotency. The composite unique constraint is the real guarantee; this
        //    just avoids doing the work (and re-sending the email) on an ordinary redelivery.
        if (notificationRecordRepository.existsByUserIdAndRecommendationId(userId, recommendationId)) {
            log.info("Notification already processed for userId={} recommendationId={}, skipping",
                    userId, recommendationId);
            return;
        }

        // 2. Who are we emailing? Absent is an expected state, not an error: the student may have
        //    registered before this service first ran, so no student.registered was ever consumed.
        Optional<UserContact> contact = userContactRepository.findByUserId(userId);

        // 3. In-app notification first, guarded against duplicates on its own side (nothing
        //    constrains the Mongo collection the way the unique index constrains Postgres).
        if (notificationDocumentRepository.findByUserIdAndRecommendationId(userId, recommendationId).isEmpty()) {
            notificationDocumentRepository.save(NotificationDocument.builder()
                    .userId(userId)
                    .recommendationId(recommendationId)
                    .title(NotificationConstants.EMAIL_SUBJECT)
                    .message(buildInAppMessage(event))
                    .notificationType(NotificationConstants.TYPE_RECOMMENDATION)
                    // Set explicitly: @CreationTimestamp is Hibernate-only and does nothing on a
                    // Mongo document, which would break the OrderByCreatedAtDesc feed ordering.
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        // 4. Email, fail-soft in both directions -- no contact, or SMTP refused.
        boolean sent = false;
        String recipientEmail = null;
        String errorMessage = null;

        if (contact.isPresent()) {
            recipientEmail = contact.get().getEmail();
            sent = emailService.sendRecommendationEmail(
                    recipientEmail,
                    displayName(contact.get()),
                    event.getTopCareerName(),
                    event.getMatchPercentage(),
                    recommendationId);
            if (!sent) {
                errorMessage = "Email send failed; see EmailService logs for the SMTP error";
            }
        } else {
            errorMessage = "No contact record for userId=" + userId
                    + " (no student.registered event consumed for this user)";
            log.warn("{} -- in-app notification still created for recommendationId={}",
                    errorMessage, recommendationId);
        }

        // 5. Audit row last.
        notificationRecordRepository.save(NotificationRecord.builder()
                .userId(userId)
                .recommendationId(recommendationId)
                .notificationType(NotificationConstants.TYPE_EMAIL)
                .status(sent ? NotificationConstants.STATUS_SENT : NotificationConstants.STATUS_FAILED)
                .recipientEmail(recipientEmail)
                .subject(NotificationConstants.EMAIL_SUBJECT)
                .errorMessage(errorMessage)
                .build());

        log.info("Processed recommendation notification userId={} recommendationId={} emailStatus={}",
                userId, recommendationId,
                sent ? NotificationConstants.STATUS_SENT : NotificationConstants.STATUS_FAILED);
    }

    /**
     * Read-modify-write rather than student-service's exists-then-skip, on purpose: a corrected
     * re-publish from auth-service (changed email, corrected name) has to actually land, and an
     * identical redelivery is then a harmless no-op overwrite. The unique constraint on userId
     * closes the read-to-write window, surfacing as a DataIntegrityViolationException that the
     * consumer's fail-soft catch swallows.
     */
    @Override
    public void upsertContact(StudentRegisteredEvent event) {
        UserContact contact = userContactRepository.findByUserId(event.getUserId())
                .orElseGet(() -> UserContact.builder().userId(event.getUserId()).build());

        contact.setEmail(event.getEmail());
        contact.setFirstName(event.getFirstName());
        contact.setLastName(event.getLastName());

        userContactRepository.save(contact);

        log.info("Stored contact details for userId={}", event.getUserId());
    }

    @Override
    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationDocumentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public NotificationResponse markAsRead(Long userId, String notificationId) {
        // userId is part of the query, so another student's notification is never loaded at all --
        // there is no window in which the wrong document sits in memory awaiting an ownership check.
        NotificationDocument document = notificationDocumentRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new CustomException("Notification not found", HttpStatus.NOT_FOUND));

        // Idempotent: re-reading an already-read notification keeps the original readAt rather
        // than sliding the timestamp forward on every call.
        if (!Boolean.TRUE.equals(document.getIsRead())) {
            document.setIsRead(true);
            document.setReadAt(LocalDateTime.now());
            document = notificationDocumentRepository.save(document);
        }

        return toResponse(document);
    }

    @Override
    public UnreadCountResponse getUnreadCount(Long userId) {
        return UnreadCountResponse.builder()
                .userId(userId)
                .unreadCount(notificationDocumentRepository.countByUserIdAndIsReadFalse(userId))
                .build();
    }

    /**
     * matchPercentage is a nullable Double and "%.1f" on null throws NPE. Null-coalesced here as
     * well as inside EmailService, because this string is built before the email path is reached
     * and an NPE here would abort the whole handler.
     */
    private String buildInAppMessage(RecommendationGeneratedEvent event) {
        String career = event.getTopCareerName() == null
                ? "a career path" : event.getTopCareerName();
        String match = event.getMatchPercentage() == null
                ? "n/a" : String.format(Locale.ROOT, "%.1f%%", event.getMatchPercentage());
        String category = event.getCategoryName() == null
                ? "your assessment" : event.getCategoryName();

        return String.format(Locale.ROOT,
                "Based on your %s assessment, your strongest match is %s (%s). "
                        + "Open CareerBridge to see your full ranking.",
                category, career, match);
    }

    private String displayName(UserContact contact) {
        String first = contact.getFirstName() == null ? "" : contact.getFirstName().trim();
        String last = contact.getLastName() == null ? "" : contact.getLastName().trim();
        return (first + " " + last).trim();
    }

    private NotificationResponse toResponse(NotificationDocument document) {
        return NotificationResponse.builder()
                .id(document.getId())
                .userId(document.getUserId())
                .recommendationId(document.getRecommendationId())
                .title(document.getTitle())
                .message(document.getMessage())
                .isRead(document.getIsRead())
                .notificationType(document.getNotificationType())
                .createdAt(document.getCreatedAt())
                .readAt(document.getReadAt())
                .build();
    }
}
