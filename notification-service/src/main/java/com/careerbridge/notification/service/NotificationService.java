package com.careerbridge.notification.service;

import com.careerbridge.notification.dto.NotificationResponse;
import com.careerbridge.notification.dto.UnreadCountResponse;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;

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

    /** The student's in-app feed, newest first. Empty list when they have none. */
    List<NotificationResponse> getMyNotifications(Long userId);

    /** 404 when the notification does not exist or belongs to someone else. */
    NotificationResponse markAsRead(Long userId, String notificationId);

    /** Unread badge count. */
    UnreadCountResponse getUnreadCount(Long userId);
}
