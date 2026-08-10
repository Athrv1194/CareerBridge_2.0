package com.careerbridge.notification.service;

import com.careerbridge.notification.dto.NotificationResponse;
import com.careerbridge.notification.dto.UnreadCountResponse;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.SessionAcceptedEvent;
import com.careerbridge.notification.event.SessionBookedEvent;
import com.careerbridge.notification.event.SessionCompletedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;
import com.careerbridge.notification.event.SubscriptionActivatedEvent;

import java.util.List;

public interface NotificationService {

    /**
     * Emails the student that their recommendation is ready and adds it to their in-app feed.
     *
     * Idempotent on (userId, recommendationId). Requires userId, recommendationId and
     * matchPercentage-safe handling; the consumer guards the nullable fields before calling.
     */
    void processRecommendationNotification(RecommendationGeneratedEvent event);

    /**
     * Records or refreshes the contact details this service emails to.
     *
     * Creates no notification -- registering is not something the student needs telling about.
     * This exists only because RecommendationGeneratedEvent carries no address.
     */
    void upsertContact(StudentRegisteredEvent event);

    /**
     * Emails the user their GST invoice (attached, when payment-service is reachable) and adds a
     * SUBSCRIPTION notification to their in-app feed.
     *
     * Idempotent on (userId, paymentId), best-effort -- see NotificationDocumentRepository. No
     * NotificationRecord audit row is written for this event type; see the class comment on
     * NotificationServiceImpl for why.
     */
    void processSubscriptionInvoice(SubscriptionActivatedEvent event);

    /**
     * Tells the MENTOR a student has requested a session with them, and adds it to the mentor's
     * in-app feed.
     *
     * Note the recipient: this is the one session notification addressed to the mentor rather than
     * the student. No NotificationRecord audit row, same reason as processSubscriptionInvoice.
     */
    void processSessionBooked(SessionBookedEvent event);

    /**
     * Tells the STUDENT their session was accepted and carries the meeting link -- the only place
     * the student receives it.
     */
    void processSessionAccepted(SessionAcceptedEvent event);

    /** Nudges the STUDENT to review a session their mentor just marked complete. */
    void processSessionCompleted(SessionCompletedEvent event);

    /** The student's in-app feed, newest first. Empty list when they have none. */
    List<NotificationResponse> getMyNotifications(Long userId);

    /** 404 when the notification does not exist or belongs to someone else. */
    NotificationResponse markAsRead(Long userId, String notificationId);

    /** Unread badge count. */
    UnreadCountResponse getUnreadCount(Long userId);
}
