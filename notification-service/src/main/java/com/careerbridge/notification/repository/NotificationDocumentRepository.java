package com.careerbridge.notification.repository;

import com.careerbridge.notification.model.NotificationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationDocumentRepository extends MongoRepository<NotificationDocument, String> {

    List<NotificationDocument> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    // Best-effort idempotency: prevents duplicate in-app feed entries on redelivery.
    Optional<NotificationDocument> findByUserIdAndRecommendationId(Long userId, Long recommendationId);

    Optional<NotificationDocument> findByUserIdAndPaymentId(Long userId, Long paymentId);

    // Ownership folded into query -- another student's document is simply not found.
    // Id is String: Mongo @Id is a 24-char ObjectId hex.
    Optional<NotificationDocument> findByIdAndUserId(String id, Long userId);
}
