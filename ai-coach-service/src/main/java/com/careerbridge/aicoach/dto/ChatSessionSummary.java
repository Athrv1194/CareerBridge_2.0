package com.careerbridge.aicoach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The list-view shape -- no messages field, matching the repository projection that excludes it
 * (ChatSessionRepository.findByStudentIdOrderByUpdatedAtDesc). Same reasoning as resume-service's
 * ResumeSummary: a list of sessions is a metadata table, not a transcript viewer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionSummary {

    private String id;
    private String careerPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
