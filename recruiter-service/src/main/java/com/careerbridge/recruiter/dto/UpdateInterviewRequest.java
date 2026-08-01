package com.careerbridge.recruiter.dto;

import com.careerbridge.recruiter.model.enums.InterviewMode;
import com.careerbridge.recruiter.model.enums.InterviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** All fields nullable: partial-update semantics, null means "leave unchanged". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInterviewRequest {

    private LocalDate scheduledDate;
    private String timeSlot;
    private InterviewMode mode;
    private String meetingLink;
    private InterviewStatus status;
    private String feedback;
}
