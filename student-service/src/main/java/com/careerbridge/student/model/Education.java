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

@Entity
@Table(name = "education")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain FK column, not a @ManyToOne: sub-entities stay independently queryable and
    // nothing cascades, which keeps the profile read a set of flat selects.
    @Column(nullable = false)
    private Long studentProfileId;

    private String institution;

    private String degree;

    private String fieldOfStudy;

    private Integer startYear;

    private Integer endYear;

    private String grade;

    // TEXT, not @Lob: Hibernate's PostgreSQLDialect maps @Lob String to oid (large object),
    // which the ORM insert path cannot fill from a plain String -- discovered while migrating
    // assessment-service's data.sql off MySQL; the same defect was latent here too.
    @Column(columnDefinition = "TEXT")
    private String description;
}
