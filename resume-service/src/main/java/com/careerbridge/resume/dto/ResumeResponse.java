package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Resume metadata. Deliberately carries no PDF bytes -- those are only ever served by
 * GET /api/resume/download/{id}, which streams them as application/pdf.
 *
 * fileUrl is derived from the id at mapping time, not stored: see StudentResume's class comment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeResponse {

    private Long id;
    private Long studentId;
    private String fileName;
    private String fileUrl;
    private Integer version;
    private Double atsScore;
    private Boolean isDefault;
    private LocalDateTime generatedAt;
}
