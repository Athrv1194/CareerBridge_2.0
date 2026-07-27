package com.careerbridge.student.model;

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

import java.time.LocalDate;

@Entity
@Table(name = "projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentProfileId;

    @Column(nullable = false)
    private String title;

    // TEXT, not @Lob: Hibernate's PostgreSQLDialect maps @Lob String to oid (large object),
    // which the ORM insert path cannot fill from a plain String.
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Comma-separated technologies -- a joined string, not a relation, to keep this flat. */
    private String techStack;

    private String projectUrl;

    private String githubUrl;

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isOngoing = false;
}
