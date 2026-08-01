package com.careerbridge.roadmap.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One student's personal copy of a RoadmapTemplate.
 *
 * A student may hold several of these at once: every assessment they complete produces its own
 * recommendation and therefore its own roadmap, and older ones stay IN_PROGRESS as history rather
 * than being closed behind their back. That is why StudentRoadmapRepository's status finder returns
 * a List, not an Optional -- an Optional finder throws IncorrectResultSizeDataAccessException the
 * moment a student takes a second assessment. Same shape recommendation-service settled on for
 * findByUserIdAndIsActiveTrue.
 *
 * The unique constraint on (student_id, recommendation_id) is the real idempotency guarantee, not
 * the existence check in RoadmapServiceImpl.generateRoadmap. RabbitMQ is at-least-once, so a
 * redelivered recommendation.generated can race that check; the constraint turns the loser into a
 * DataIntegrityViolationException the consumer's fail-soft catch discards. Same design as
 * student_profiles.userId in student-service.
 */
@Entity
@Table(
        name = "student_roadmaps",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_roadmap_recommendation",
                columnNames = {"student_id", "recommendation_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRoadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long recommendationId;

    // TEXT, not @Lob -- see RoadmapTemplate.careerName. Denormalised from the template on purpose:
    // the roadmap must keep naming the career it was generated for even if the template is renamed.
    @Column(columnDefinition = "TEXT", nullable = false)
    private String careerName;

    /**
     * IN_PROGRESS, COMPLETED or PAUSED. A String, not an enum, so adding a state is a data change
     * rather than a redeploy of this service plus every consumer of roadmap.updated.
     */
    @Builder.Default
    @Column(nullable = false)
    private String status = "IN_PROGRESS";

    @Column(nullable = false)
    private Integer totalMilestones;

    @Builder.Default
    @Column(nullable = false)
    private Integer completedMilestones = 0;

    /** Feeds the future Placement Readiness Score at 30% weight. Nothing consumes it yet. */
    @Builder.Default
    @Column(nullable = false)
    private Double completionPercentage = 0.0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime startedAt;

    /** Null until the last milestone is ticked off. */
    private LocalDateTime completedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Excluded from toString/equals to break the @Data recursion; see RoadmapTemplate. */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "studentRoadmap", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StudentMilestone> milestones = new ArrayList<>();
}
