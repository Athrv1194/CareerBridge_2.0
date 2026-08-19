package com.careerbridge.recruiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Bytes plus content type and original filename for a résumé attached to one application. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeFileBlob {
    private byte[] bytes;
    private String contentType;
    private String fileName;
}
