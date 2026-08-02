package com.careerbridge.resume.pdf;

import com.careerbridge.resume.dto.CertificateDto;
import com.careerbridge.resume.dto.EducationDto;
import com.careerbridge.resume.dto.ProjectDto;
import com.careerbridge.resume.dto.SkillDto;
import com.careerbridge.resume.dto.StudentProfileDto;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders a one-page A4 resume from a student profile.
 *
 * OpenPDF (com.lowagie.text), not iText 7: iText Community is AGPL v3, and serving a generated PDF
 * over HTTP would oblige publishing this repository's source. OpenPDF is an LGPL/MPL fork of the
 * iText 4 lineage, so the flowing Document/Paragraph model is the same shape.
 *
 * Only the built-in Helvetica family is used. Never load a font from the filesystem -- the path
 * that exists on a developer machine does not exist in the container, and the failure is a
 * file-not-found at generation time rather than at startup.
 *
 * Every section null-checks and skips itself when empty, so a brand-new profile with nothing but a
 * name still produces a valid PDF rather than throwing. That property is pinned by a test.
 *
 * The ATS score is deliberately NOT rendered. It is internal placement-readiness metadata; a
 * recruiter reading a candidate's resume has no business seeing the platform's own scoring of it.
 *
 * OpenPDF's DocumentException is wrapped as IOException so the service layer never imports an
 * OpenPDF type -- swapping the PDF library later stays confined to this package.
 */
@Component
public class ResumePdfBuilder {

    private static final Color PRIMARY = new Color(46, 116, 181);
    private static final Color TEXT_DARK = new Color(33, 33, 33);
    private static final Color TEXT_GRAY = new Color(100, 100, 100);
    private static final Color RULE = new Color(200, 200, 200);

    private static final Font NAME_FONT = new Font(Font.HELVETICA, 22, Font.BOLD, PRIMARY);
    private static final Font CONTACT_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, TEXT_GRAY);
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, PRIMARY);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, TEXT_DARK);
    private static final Font ITEM_TITLE_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, TEXT_DARK);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_DARK);
    private static final Font SMALL_ITALIC_GRAY = new Font(Font.HELVETICA, 9, Font.ITALIC, TEXT_GRAY);
    private static final Font SMALL_GRAY = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_GRAY);

    private static final DateTimeFormatter MONTH_YEAR =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    /** Roughly three lines at 9pt across the A4 text width. Longer descriptions are truncated. */
    private static final int MAX_DESCRIPTION_CHARS = 300;

    private static final String SKILL_SEPARATOR = "   •   ";

    /**
     * @throws IOException if the PDF cannot be written; the service maps this to a 500.
     */
    public byte[] build(StudentProfileDto profile) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Rectangle, then left, right, top, bottom -- that is OpenPDF's argument order, not the
        // CSS top/right/bottom/left one.
        Document document = new Document(PageSize.A4, 50, 50, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, profile);
            addSkills(document, profile);
            addEducation(document, profile);
            addProjects(document, profile);
            addCertificates(document, profile);
            addLinks(document, profile);
        } catch (DocumentException ex) {
            throw new IOException("Failed to render resume PDF", ex);
        } finally {
            // close() flushes the trailer; without it the byte array is an unreadable partial file.
            if (document.isOpen()) {
                document.close();
            }
        }

        return out.toByteArray();
    }

    private void addHeader(Document document, StudentProfileDto profile) throws DocumentException {
        String fullName = join(" ", profile.getFirstName(), profile.getLastName());
        Paragraph name = new Paragraph(fullName.isBlank() ? "Resume" : fullName, NAME_FONT);
        name.setAlignment(Element.ALIGN_CENTER);
        document.add(name);

        // Only the contact parts that exist, so a student with no phone gets "email | city",
        // never "email |  | city".
        String contact = join(" | ", profile.getEmail(), profile.getPhone(), profile.getCity());
        if (!contact.isBlank()) {
            Paragraph contactLine = new Paragraph(contact, CONTACT_FONT);
            contactLine.setAlignment(Element.ALIGN_CENTER);
            contactLine.setSpacingBefore(2f);
            document.add(contactLine);
        }

        if (StringUtils.hasText(profile.getBio())) {
            Paragraph bio = new Paragraph(truncate(profile.getBio()), SMALL_FONT);
            bio.setAlignment(Element.ALIGN_CENTER);
            bio.setSpacingBefore(6f);
            document.add(bio);
        }

        addSeparator(document);
    }

    private void addSkills(Document document, StudentProfileDto profile) throws DocumentException {
        List<SkillDto> skills = profile.getSkills();
        if (isEmpty(skills)) {
            return;
        }

        // The null-element filter comes first, matching the other sections: a null entry inside the
        // list is a different failure from a null list, and getSkillName on one is an NPE.
        String joined = skills.stream()
                .filter(Objects::nonNull)
                .map(SkillDto::getSkillName)
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + SKILL_SEPARATOR + b)
                .orElse("");

        if (joined.isBlank()) {
            return;
        }

        addHeading(document, "SKILLS");
        document.add(new Paragraph(joined, BODY_FONT));
        addSeparator(document);
    }

    private void addEducation(Document document, StudentProfileDto profile) throws DocumentException {
        List<EducationDto> educations = profile.getEducations();
        if (isEmpty(educations)) {
            return;
        }

        addHeading(document, "EDUCATION");
        for (EducationDto education : educations) {
            if (education == null) {
                continue;
            }

            String qualification = join(", ", education.getDegree(), education.getFieldOfStudy());
            String years = formatYears(education.getStartYear(), education.getEndYear());
            String line = join(" - ", qualification, education.getInstitution());
            if (!years.isBlank()) {
                line = line + " (" + years + ")";
            }

            if (!line.isBlank()) {
                document.add(itemParagraph(line, ITEM_TITLE_FONT));
            }
            if (StringUtils.hasText(education.getGrade())) {
                document.add(new Paragraph("Grade: " + education.getGrade(), SMALL_GRAY));
            }
        }
        addSeparator(document);
    }

    private void addProjects(Document document, StudentProfileDto profile) throws DocumentException {
        List<ProjectDto> projects = profile.getProjects();
        if (isEmpty(projects)) {
            return;
        }

        addHeading(document, "PROJECTS");
        for (ProjectDto project : projects) {
            if (project == null || !StringUtils.hasText(project.getTitle())) {
                continue;
            }

            document.add(itemParagraph(project.getTitle(), ITEM_TITLE_FONT));

            if (StringUtils.hasText(project.getTechStack())) {
                document.add(new Paragraph(project.getTechStack(), SMALL_ITALIC_GRAY));
            }
            if (StringUtils.hasText(project.getDescription())) {
                document.add(new Paragraph(truncate(project.getDescription()), SMALL_FONT));
            }
            if (StringUtils.hasText(project.getGithubUrl())) {
                document.add(new Paragraph(project.getGithubUrl(), SMALL_GRAY));
            }
        }
        addSeparator(document);
    }

    private void addCertificates(Document document, StudentProfileDto profile) throws DocumentException {
        List<CertificateDto> certificates = profile.getCertificates();
        if (isEmpty(certificates)) {
            return;
        }

        addHeading(document, "CERTIFICATIONS");
        for (CertificateDto certificate : certificates) {
            if (certificate == null || !StringUtils.hasText(certificate.getName())) {
                continue;
            }

            String line = join(" - ", certificate.getName(), certificate.getIssuingOrganization());
            String issued = formatMonthYear(certificate.getIssueDate());
            if (!issued.isBlank()) {
                line = line + " (" + issued + ")";
            }

            document.add(new Paragraph(line, BODY_FONT));
        }
        addSeparator(document);
    }

    /**
     * Built from the flat linkedinUrl/githubUrl/portfolioUrl fields on the profile, not from the
     * SocialLink entity -- that entity exists in student-service but is dead code, never returned
     * by any endpoint.
     */
    private void addLinks(Document document, StudentProfileDto profile) throws DocumentException {
        List<String> links = new ArrayList<>();
        addLinkIfPresent(links, "LinkedIn", profile.getLinkedinUrl());
        addLinkIfPresent(links, "GitHub", profile.getGithubUrl());
        addLinkIfPresent(links, "Portfolio", profile.getPortfolioUrl());

        if (links.isEmpty()) {
            return;
        }

        addHeading(document, "LINKS");
        for (String link : links) {
            document.add(new Paragraph(link, BODY_FONT));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private void addLinkIfPresent(List<String> links, String label, String url) {
        if (StringUtils.hasText(url)) {
            links.add(label + ": " + url);
        }
    }

    private void addHeading(Document document, String title) throws DocumentException {
        Paragraph heading = new Paragraph(title, HEADING_FONT);
        heading.setSpacingBefore(10f);
        heading.setSpacingAfter(4f);
        document.add(heading);
    }

    private Paragraph itemParagraph(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(4f);
        return paragraph;
    }

    private void addSeparator(Document document) throws DocumentException {
        document.add(new Chunk(new LineSeparator(0.5f, 100f, RULE, Element.ALIGN_CENTER, -2f)));
    }

    /** Joins only the parts that have text, so a null in the middle never leaves a dangling delimiter. */
    private String join(String delimiter, String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                present.add(part.trim());
            }
        }
        return String.join(delimiter, present);
    }

    private String formatYears(Integer startYear, Integer endYear) {
        if (startYear == null && endYear == null) {
            return "";
        }
        if (startYear == null) {
            return String.valueOf(endYear);
        }
        if (endYear == null) {
            return startYear + " - Present";
        }
        return startYear + " - " + endYear;
    }

    private String formatMonthYear(LocalDate date) {
        return date == null ? "" : date.format(MONTH_YEAR);
    }

    private String truncate(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= MAX_DESCRIPTION_CHARS
                ? trimmed
                : trimmed.substring(0, MAX_DESCRIPTION_CHARS).trim() + "...";
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
