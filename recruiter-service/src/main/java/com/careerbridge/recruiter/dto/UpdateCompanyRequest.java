package com.careerbridge.recruiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** All fields nullable: partial-update semantics, null means "leave unchanged". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyRequest {

    private String name;
    private String industry;
    private String website;
    private String description;
    private String logoUrl;
}
