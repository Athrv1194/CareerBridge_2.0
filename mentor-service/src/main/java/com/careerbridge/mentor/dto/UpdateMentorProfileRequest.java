package com.careerbridge.mentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update: every field is optional and null means "leave unchanged", never "clear".
 *
 * Same contract as organization-service's UpdateOrganizationRequest, and for the same reason -- a
 * full-replace shape would wipe a mentor's bio every time they toggled their availability.
 * firstName/lastName are absent deliberately: renaming the person behind a profile that already has
 * reviews attached is an identity change, not a profile edit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMentorProfileRequest {

    private String bio;
    private String currentCompany;
    private String currentRole;
    private Integer yearsOfExperience;
    private String expertiseAreas;
    private String careerPaths;
    private String linkedinUrl;
    private Boolean isAvailable;
}
