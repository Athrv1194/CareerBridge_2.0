package com.careerbridge.recruiter.dto;

import com.careerbridge.recruiter.model.enums.JobType;
import com.careerbridge.recruiter.model.enums.WorkMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {

    @NotNull(message = "companyId is required")
    private Long companyId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    /** Comma-separated, e.g. "Java,Spring Boot,PostgreSQL". */
    private String requiredSkills;

    private String location;

    @NotNull(message = "workMode is required")
    private WorkMode workMode;

    @NotNull(message = "jobType is required")
    private JobType jobType;

    private BigDecimal salaryMin;

    private BigDecimal salaryMax;

    private LocalDate applicationDeadline;
}
