package com.careerbridge.mentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorshipSessionResponse {

    private Long id;
    private Long studentId;
    private Long mentorUserId;

    /** Embedded so a student's session list renders without a second call per row. */
    private MentorProfileResponse mentorProfile;

    private String topic;
    private LocalDateTime scheduledAt;
    private Integer durationMinutes;

    /** REQUESTED, ACCEPTED, COMPLETED, DECLINED or CANCELLED. */
    private String status;

    private String meetingLink;
    private String mentorNotes;
    private LocalDateTime createdAt;
}
