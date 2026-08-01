package com.careerbridge.recruiter.dto;

import com.careerbridge.recruiter.model.enums.JobType;
import com.careerbridge.recruiter.model.enums.WorkMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * List-endpoint shape: no description and no requiredSkills, both of which are TEXT columns that
 * would dominate the payload of a job board listing. Clients fetch GET /jobs/{id} for those.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSummaryResponse {

    private Long id;
    private Long companyId;
    private String companyName;
    private String title;
    private String location;
    private WorkMode workMode;
    private JobType jobType;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private LocalDate applicationDeadline;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
