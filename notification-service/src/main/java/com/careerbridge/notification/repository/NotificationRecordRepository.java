package com.careerbridge.notification.repository;

import com.careerbridge.notification.model.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Must extend JpaRepository explicitly, not CrudRepository. This service has both JPA and Mongo
 * repositories, so Spring Data runs in strict configuration mode and picks the module by the base
 * interface and the entity annotation. A repository extending only CrudRepository matches neither
 * module, and the failure is silent -- no bean is created and nothing is logged; it surfaces much
 * later as NoSuchBeanDefinitionException at injection.
 */
public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    /** Operator-facing delivery history for one student, newest first. */
    List<NotificationRecord> findByUserIdOrderBySentAtDesc(Long userId);

    /**
     * Idempotency fast path for the consumer. Best-effort only -- it races redelivery -- with the
     * composite unique constraint on (user_id, recommendation_id) as the actual guarantee.
     */
    boolean existsByUserIdAndRecommendationId(Long userId, Long recommendationId);
}
