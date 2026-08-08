package com.careerbridge.student.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Serves as both request body (POST .../certificates) and response element. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateDto {

    private Long id;

    @NotBlank(message = "Certificate name is required")
    private String name;

    private String issuingOrganization;

    private LocalDate issueDate;

    /** Null means the credential does not expire. */
    private LocalDate expiryDate;

    private String credentialUrl;
}
