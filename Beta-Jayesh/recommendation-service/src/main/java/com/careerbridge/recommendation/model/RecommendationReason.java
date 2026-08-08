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

/** The one-per-recommendation narrative: what the student is good at, what to fix, what to do. */
@Entity
@Table(name = "recommendation_reasons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique because there is exactly one reason row per recommendation, and because
     * findByRecommendationId returns an Optional -- a second row would make that finder throw
     * IncorrectResultSizeDataAccessException on read rather than fail loudly on write.
     */
    @Column(nullable = false, unique = true)
    private Long recommendationId;

    /** All three are generated prose: 255 overflows once a category name is interpolated in. */
    @Column(nullable = false, length = 500)
    private String strengthArea;

    @Column(nullable = false, length = 500)
    private String improvementArea;

    @Column(nullable = false, length = 500)
    private String actionableAdvice;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
