package com.careerbridge.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The rendered PDF bytes plus the filename to serve them under. Same shape as resume-service's
 * ResumeDownload, for the same reason: one repository read produces both, so there is no second
 * query just to learn the filename for Content-Disposition and no window for the RBAC check to
 * disagree with itself between two calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDownload {

    private String fileName;
    private byte[] content;
}
