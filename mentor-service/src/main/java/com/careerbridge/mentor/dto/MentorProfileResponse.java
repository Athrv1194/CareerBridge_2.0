package com.careerbridge.mentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * expertiseAreas and careerPaths are Lists here but comma-delimited TEXT in the database -- split
 * once in the response mapper so no client ever has to parse a delimiter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfileResponse {

    private Long id;
    private Long userId;
    private String firstName;
    private String lastName;
    private String bio;
    private String currentCompany;
    private String currentRole;
    private Integer yearsOfExperience;
    private List<String> expertiseAreas;
    private List<String> careerPaths;
    private String linkedinUrl;
    private Boolean isAvailable;
    private Integer sessionsCompleted;
    private BigDecimal averageRating;
    private LocalDateTime createdAt;
}
