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

    // Emails student + adds in-app notification. Idempotent on (userId, recommendationId).
    void processRecommendationNotification(RecommendationGeneratedEvent event);

    // Stores contact details for later lookups -- RecommendationGeneratedEvent carries no email.
    void upsertContact(StudentRegisteredEvent event);

    void processSubscriptionInvoice(SubscriptionActivatedEvent event);

    // Note: session booked notifies the MENTOR, not the student.
    void processSessionBooked(SessionBookedEvent event);

    // Carries the meeting link -- only place the student receives it.
    void processSessionAccepted(SessionAcceptedEvent event);

    void processSessionCompleted(SessionCompletedEvent event);

    List<NotificationResponse> getMyNotifications(Long userId);

    // 404 when notification doesn't exist or belongs to another user.
    NotificationResponse markAsRead(Long userId, String notificationId);

    UnreadCountResponse getUnreadCount(Long userId);
}
