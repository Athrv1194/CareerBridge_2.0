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
 * A student may hold several of these at once -- one per career they've chosen to build a roadmap
 * for, via POST /api/roadmap. That is why StudentRoadmapRepository's finders return a List, not an
 * Optional where more than one is possible.
 *
 * The unique constraint on (student_id, career_name) is the real idempotency guarantee, not the
 * existence check in RoadmapServiceImpl.buildRoadmap: it's what makes "build" safe to call twice for
 * the same career and get the same roadmap back rather than a duplicate. One roadmap per student per
 * career, persisting across retaken assessments -- a new recommendation does not reset progress on a
 * roadmap the student already started. Same idempotency shape as student_profiles.userId in
 * student-service.
 *
 * ponytail: no recommendationId anymore. Roadmaps used to be created only by consuming
 * recommendation.generated, tied 1:1 to the recommendation that produced them; now they're built
 * on-demand for whichever career the student clicks, so that link no longer exists and nothing else
 * ever read the column. If provenance ("which recommendation led to this") becomes worth tracking
 * again, add it back as a nullable column, not a NOT NULL one -- see the Question.updatedAt incident
 * pattern documented elsewhere in this project for why.
 */
@Entity
@Table(
        name = "student_roadmaps",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_roadmap_career",
                columnNames = {"student_id", "career_name"}))
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
