package com.careerbridge.notification.repository;

import com.careerbridge.notification.model.NotificationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Must extend MongoRepository explicitly -- see the note on NotificationRecordRepository about
 * strict multi-module detection and the silent no-bean failure.
 */
public interface NotificationDocumentRepository extends MongoRepository<NotificationDocument, String> {

    /** Backs GET /my -- the student's feed, newest first. */
    List<NotificationDocument> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Backs GET /unread-count. Returns long, so UnreadCountResponse.unreadCount is a long too. */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * Mongo-side idempotency guard. The NotificationRecord unique constraint protects the Postgres
     * row, but nothing constrains this collection, so the in-app notification is written only when
     * this returns empty -- otherwise a redelivered event would leave the student with duplicates
     * in their feed.
     */
    Optional<NotificationDocument> findByUserIdAndRecommendationId(Long userId, Long recommendationId);

    /**
     * Same best-effort idempotency guard as findByUserIdAndRecommendationId, for subscription
     * invoice notifications -- races redelivery rather than closing it, which is acceptable here
     * for the same reason it is acceptable there.
     */
    Optional<NotificationDocument> findByUserIdAndPaymentId(Long userId, Long paymentId);

    /**
     * Ownership folded into the query, so another student's notification is simply not found
     * rather than fetched and then compared -- there is no window where the wrong document sits
     * in memory. Id is a String because Mongo's @Id is a 24-char ObjectId hex.
     */
    Optional<NotificationDocument> findByIdAndUserId(String id, Long userId);
}
