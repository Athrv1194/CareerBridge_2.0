package com.careerbridge.recruiter.model;

import com.careerbridge.recruiter.model.enums.InterviewMode;
import com.careerbridge.recruiter.model.enums.InterviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Deliberately NO unique constraint on application_id: a student can go through multiple
 * interview rounds for the same application (round 1 while SHORTLISTED, round 2+ once the
 * recruiter has moved them to INTERVIEWED). Every InterviewRepository finder therefore returns a
 * List, ordered by scheduledDate -- a single-result finder needs a unique constraint behind the
 * queried column, and this one deliberately has none. Adding a one-interview-per-application rule
 * later means adding the constraint, not just a check in the service.
 */
@Entity
@Table(name = "interviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private String timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewMode mode;

    private String meetingLink;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
