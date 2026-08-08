package com.careerbridge.recruiter.event;

import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusUpdatedEvent {

    private Long applicationId;
    private Long studentId;
    private Long jobId;
    private ApplicationStatus newStatus;
    private LocalDateTime updatedAt;
}
