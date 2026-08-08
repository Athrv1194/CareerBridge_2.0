package com.careerbridge.roadmap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One step of one student's roadmap, snapshotted from a MilestoneTemplate at generation time.
 *
 * studentId is denormalised from the parent roadmap deliberately: the ownership check in
 * completeMilestone is then a single load rather than a load plus a join, and it is what makes a
 * cross-student PATCH a 403 instead of silently succeeding.
 */
@Entity
@Table(name = "student_milestones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private Integer estimatedDays;

    @Column(columnDefinition = "TEXT")
    private String resourceUrl;

    /**
     * Boolean, never a primitive boolean. A primitive makes Lombok emit isCompleted() instead of
     * getIsCompleted(), which collapses the JSON property to "completed" and silently breaks the
     * frontend contract. The missing @Builder.Default would write null, and a null here is neither
     * true nor false to countByStudentRoadmapIdAndIsCompletedTrue. Both traps are pinned by tests;
     * same lesson as notification-service's isRead.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isCompleted = false;

    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Excluded from toString/equals to break the @Data recursion; see StudentRoadmap. */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_roadmap_id", nullable = false)
    private StudentRoadmap studentRoadmap;
}
