package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The PDF bytes plus the filename to serve them under.
 *
 * Exists so downloadResume is a single repository read. Returning bare byte[] would force the
 * controller to call getResumeById as well just to learn the fileName for Content-Disposition --
 * two queries, and two chances for the RBAC check to disagree with itself between them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDownload {

    private String fileName;
    private byte[] content;
}
