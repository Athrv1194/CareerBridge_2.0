package com.careerbridge.recruiter.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSubmittedEvent {

    private Long jobId;
    private Long studentId;
    private Long recruiterId;
    private LocalDateTime submittedAt;
}
