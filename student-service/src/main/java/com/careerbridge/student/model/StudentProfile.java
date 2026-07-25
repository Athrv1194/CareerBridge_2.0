package com.careerbridge.student.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The auth-service user this profile belongs to. Unique is load-bearing, not decorative:
     * it is the actual idempotency guarantee for the registration consumer, which can receive
     * the same event twice and would otherwise create a duplicate profile on the race.
     */
    @Column(unique = true, nullable = false)
    private Long userId;

    private String firstName;

    private String lastName;

    private String email;

    private String phone;

    // Free text well past the 255-char column default.
    @Lob
    private String bio;

    private String city;

    private String state;

    private String country;

    private String linkedinUrl;

    private String githubUrl;

    private String portfolioUrl;

    private String resumeUrl;

    @Builder.Default
    @Column(nullable = false)
    private Integer profileCompletionPercentage = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isPublic = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
