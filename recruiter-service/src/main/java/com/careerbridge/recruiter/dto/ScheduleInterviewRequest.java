package com.careerbridge.recruiter.dto;

import com.careerbridge.recruiter.model.enums.InterviewMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleInterviewRequest {

    @NotNull(message = "scheduledDate is required")
    private LocalDate scheduledDate;

    @NotBlank(message = "timeSlot is required")
    private String timeSlot;

    @NotNull(message = "mode is required")
    private InterviewMode mode;

    /**
     * Not @NotBlank: only required when mode is ONLINE, which bean validation cannot express as a
     * cross-field rule without a custom ConstraintValidator. Enforced in InterviewServiceImpl,
     * which returns the same 400 shape a validation failure would.
     */
    private String meetingLink;
}
