package com.careerbridge.recommendation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /**
     * Unique, and the real idempotency guarantee. attemptId is assessment-service's primary key, so
     * one attempt yields exactly one recommendation no matter how often RabbitMQ redelivers the
     * event. The consumer's existsByAssessmentAttemptId check is only a fast path with a TOCTOU
     * window; this constraint is what the database actually enforces, surfacing as the
     * DataIntegrityViolationException the consumer swallows.
     */
    @Column(nullable = false, unique = true)
    private Long assessmentAttemptId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String categoryName;

    /**
     * Always equal to the rank-1 CareerRanking's matchPercentage -- both are derived from this
     * service's own ranking, never copied from the event's careerMatchPercentage, so the response
     * cannot contradict itself.
     */
    @Column(nullable = false)
    private Double overallMatchPercentage;

    @Column(nullable = false)
    private String topCareerName;

    /**
     * Only the newest recommendation per user is active; superseded ones are flipped false rather
     * than deleted, which is what makes GET /history work.
     *
     * @Builder.Default is load-bearing: without it Lombok's builder ignores this initializer and
     * writes null, violating the nullable = false column.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    /** Stamped per row so old recommendations stay interpretable after the algorithm changes. */
    @Builder.Default
    @Column(nullable = false)
    private String algorithmVersion = "v1.0";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
