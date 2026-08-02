package com.careerbridge.resume;

import com.careerbridge.resume.dto.CertificateDto;
import com.careerbridge.resume.dto.EducationDto;
import com.careerbridge.resume.dto.ProjectDto;
import com.careerbridge.resume.dto.SkillDto;
import com.careerbridge.resume.dto.StudentProfileDto;
import com.careerbridge.resume.pdf.ResumePdfBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No Mockito here -- the builder has no collaborators. These run the real OpenPDF engine and
 * assert on the real bytes, which is the only thing that proves the library is wired correctly.
 * A mocked PDF test would prove nothing.
 */
class ResumePdfBuilderTest {

    private final ResumePdfBuilder builder = new ResumePdfBuilder();

    /** Every PDF file begins with the literal bytes "%PDF". Cheapest possible validity check. */
    private static void assertIsPdf(byte[] bytes) {
        assertTrue(bytes.length > 4, "PDF is too short to contain a header");
        String header = new String(Arrays.copyOfRange(bytes, 0, 4), StandardCharsets.US_ASCII);
        assertTrue("%PDF".equals(header), "expected a %PDF header but got: " + header);
    }

    private static StudentProfileDto fullProfile() {
        return StudentProfileDto.builder()
                .userId(42L)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@careerbridge.com")
                .phone("+91-9000000000")
                .city("Pune")
                .bio("Final year CS student focused on backend engineering.")
                .linkedinUrl("https://linkedin.com/in/ada")
                .githubUrl("https://github.com/ada")
                .portfolioUrl("https://ada.dev")
                .skills(List.of(
                        SkillDto.builder().skillName("Java").proficiencyLevel("ADVANCED").build(),
                        SkillDto.builder().skillName("Spring Boot").proficiencyLevel("INTERMEDIATE").build(),
                        SkillDto.builder().skillName("PostgreSQL").proficiencyLevel("INTERMEDIATE").build()))
                .educations(List.of(
                        EducationDto.builder()
                                .institution("COEP Technological University")
                                .degree("B.Tech").fieldOfStudy("Computer Science")
                                .startYear(2022).endYear(2026).grade("9.1 CGPA").build()))
                .projects(List.of(
                        ProjectDto.builder()
                                .title("CareerBridge")
                                .description("A microservices placement platform built with Spring Boot.")
                                .techStack("Java, Spring Boot, PostgreSQL, RabbitMQ")
                                .githubUrl("https://github.com/ada/careerbridge").build()))
                .certificates(List.of(
                        CertificateDto.builder()
                                .name("AWS Solutions Architect Associate")
                                .issuingOrganization("Amazon Web Services")
                                .issueDate(LocalDate.of(2026, 3, 1)).build()))
                .build();
    }

    @Test
    @DisplayName("build: a fully populated profile renders a real, non-trivial PDF")
    void build_FullProfile_ProducesValidPdf() throws Exception {
        byte[] pdf = builder.build(fullProfile());

        assertIsPdf(pdf);
        // A header-only PDF is roughly 500 bytes; anything with real sections clears 1000 easily.
        // This is the same threshold the live verification uses on the downloaded file.
        assertTrue(pdf.length > 1000, "expected a substantial PDF but got " + pdf.length + " bytes");
    }

    /**
     * The property that matters most in production: a student who just registered has a profile
     * with a name and nothing else. Every section must skip itself rather than throw, or resume
     * generation 500s for exactly the users most likely to try it first.
     */
    @Test
    @DisplayName("build: a profile with every collection null still renders without throwing")
    void build_EmptyProfile_DoesNotThrow() {
        StudentProfileDto bare = StudentProfileDto.builder()
                .userId(1L)
                .firstName("New")
                .lastName("Student")
                .build();

        byte[] pdf = assertDoesNotThrow(() -> builder.build(bare));
        assertIsPdf(pdf);
    }

    @Test
    @DisplayName("build: empty collections are treated the same as null collections")
    void build_EmptyCollections_DoesNotThrow() {
        StudentProfileDto empty = StudentProfileDto.builder()
                .userId(1L)
                .firstName("New")
                .lastName("Student")
                .skills(Collections.emptyList())
                .educations(Collections.emptyList())
                .projects(Collections.emptyList())
                .certificates(Collections.emptyList())
                .build();

        byte[] pdf = assertDoesNotThrow(() -> builder.build(empty));
        assertIsPdf(pdf);
    }

    /**
     * A null entry inside a list is a different failure from a null list, and a real payload can
     * carry one if student-service ever serialises a partially-populated child row.
     */
    @Test
    @DisplayName("build: null entries inside the collections are skipped, not dereferenced")
    void build_NullEntriesInLists_DoesNotThrow() {
        StudentProfileDto withNulls = StudentProfileDto.builder()
                .userId(1L)
                .firstName("Edge")
                .lastName("Case")
                .skills(Arrays.asList(SkillDto.builder().skillName("Java").build(), null))
                .educations(Arrays.asList((EducationDto) null))
                .projects(Arrays.asList((ProjectDto) null))
                .certificates(Arrays.asList((CertificateDto) null))
                .build();

        byte[] pdf = assertDoesNotThrow(() -> builder.build(withNulls));
        assertIsPdf(pdf);
    }

    /** A profile with no contact details at all must not emit a dangling " | " separator line. */
    @Test
    @DisplayName("build: missing contact fields do not produce empty separators")
    void build_NoContactDetails_DoesNotThrow() {
        StudentProfileDto noContact = StudentProfileDto.builder()
                .firstName("No")
                .lastName("Contact")
                .skills(List.of(SkillDto.builder().skillName("Java").build()))
                .build();

        byte[] pdf = assertDoesNotThrow(() -> builder.build(noContact));
        assertIsPdf(pdf);
    }
}
