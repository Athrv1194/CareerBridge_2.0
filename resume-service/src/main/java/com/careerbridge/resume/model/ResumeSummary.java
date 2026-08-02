package com.careerbridge.resume.model;

import java.time.LocalDateTime;

/**
 * Spring Data interface projection over StudentResume, deliberately omitting pdfContent.
 *
 * The first projection in this codebase -- every other repository returns full entities. Justified
 * here because pdfContent is a byte[] with no lazy-loading option (JPA has no @Basic(fetch=LAZY)
 * guarantee across providers, and Hibernate would need bytecode enhancement to honour it even where
 * declared). A plain entity finder for a list of resumes would pull every PDF into memory just to
 * render a metadata table. Spring Data generates the narrower SELECT from this interface's getter
 * set at proxy time -- no @Query needed.
 *
 * Method names must exactly match StudentResume's getters (minus pdfContent) for Spring Data to
 * wire the projection.
 */
public interface ResumeSummary {

    Long getId();

    Long getStudentId();

    String getFileName();

    Integer getVersion();

    Double getAtsScore();

    Boolean getIsDefault();

    LocalDateTime getGeneratedAt();
}
