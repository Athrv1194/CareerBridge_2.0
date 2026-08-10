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
public class SessionReviewResponse {

    private Long id;
    private Long sessionId;
    private Long studentId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
