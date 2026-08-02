package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Mirrors student-service's CertificateDto field-for-field. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateDto {

    private String name;
    private String issuingOrganization;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String credentialUrl;
}
