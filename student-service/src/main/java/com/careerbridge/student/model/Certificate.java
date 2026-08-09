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
@Table(name = "certificates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentProfileId;

    @Column(nullable = false)
    private String name;

    private String issuingOrganization;

    private LocalDate issueDate;

    /** Null means the credential does not expire. */
    private LocalDate expiryDate;

    private String credentialUrl;

    // Nullable, added to an already-populated table -- same reasoning as StudentProfile.avatarImage.
    // Lets a student attach the actual certificate/offer-letter PDF, separate from credentialUrl
    // (a plain link).
    @Column(columnDefinition = "bytea")
    private byte[] credentialFile;

    private String credentialFileName;

    private String credentialFileContentType;
}
