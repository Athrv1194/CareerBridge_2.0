package com.careerbridge.mentor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One-time setup after a MENTOR registers.
 *
 * firstName/lastName are supplied here rather than fetched from auth-service: this service makes no
 * synchronous cross-service calls, and the name a mentor wants shown publicly is not necessarily
 * the one on their account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMentorProfileRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String bio;

    @NotBlank(message = "Current company is required")
    private String currentCompany;

    @NotBlank(message = "Current role is required")
    private String currentRole;

    @NotNull(message = "Years of experience is required")
    @Min(value = 0, message = "Years of experience cannot be negative")
    @Max(value = 50, message = "Years of experience cannot exceed 50")
    private Integer yearsOfExperience;

    /** Comma-separated, e.g. "Java,Spring Boot,System Design". */
    @NotBlank(message = "At least one expertise area is required")
    private String expertiseAreas;

    /** Comma-separated career paths this mentor covers. */
    @NotBlank(message = "At least one career path is required")
    private String careerPaths;

    private String linkedinUrl;
}
