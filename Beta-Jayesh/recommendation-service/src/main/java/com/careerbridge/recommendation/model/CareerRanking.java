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
@Table(name = "career_rankings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recommendationId;

    /** Denormalised name, not an FK: the career catalogue is a constant here, not a table. */
    @Column(nullable = false)
    private String careerName;

    @Column(nullable = false)
    private Double matchPercentage;

    /**
     * 1-based position, ascending by rank = descending by matchPercentage.
     *
     * The column name MUST stay quoted. RANK is a reserved word in MySQL 8.0.2+ (window functions)
     * and MySQL 9.0.1 rejects it unquoted with error 1064; Hibernate's auto-quoting is off by
     * default (hibernate.auto_quote_keyword) and nothing in this project enables it, so an
     * unadorned column name makes ddl-auto fail at startup. The escaped double quotes mark the
     * identifier pre-quoted, and Hibernate re-renders it with MySQL's backticks.
     *
     * The Java property stays `rank`, so the derived query findByRecommendationIdOrderByRankAsc and
     * the JSON field name are both unaffected -- Spring Data parses the property, not the column.
     */
    @Column(name = "\"rank\"", nullable = false)
    private Integer rank;

    /** True when rank <= RecommendationConstants.TOP_CAREERS_COUNT. Purely a rank concept. */
    @Column(nullable = false)
    private Boolean isTopRecommendation;

    /** Generated prose; 255 is not enough once a career and category name are interpolated in. */
    @Column(nullable = false, length = 500)
    private String reasonText;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
