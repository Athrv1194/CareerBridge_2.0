package com.careerbridge.recruiter.dto;

import com.careerbridge.recruiter.model.enums.JobType;
import com.careerbridge.recruiter.model.enums.WorkMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** All fields nullable, no companyId (immutable after creation): partial-update semantics. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateJobRequest {

    private String title;
    private String description;
    private String requiredSkills;
    private String location;
    private WorkMode workMode;
    private JobType jobType;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private LocalDate applicationDeadline;
}
