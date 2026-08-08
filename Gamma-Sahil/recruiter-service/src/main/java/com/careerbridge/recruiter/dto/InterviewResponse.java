package com.careerbridge.recruiter.dto;

import com.careerbridge.recruiter.model.enums.InterviewMode;
import com.careerbridge.recruiter.model.enums.InterviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResponse {

    private Long id;
    private Long applicationId;
    private String jobTitle;
    private Long studentId;
    private LocalDate scheduledDate;
    private String timeSlot;
    private InterviewMode mode;
    private String meetingLink;
    private InterviewStatus status;
    private String feedback;
    private LocalDateTime createdAt;
}
