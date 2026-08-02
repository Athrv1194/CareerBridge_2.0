package com.careerbridge.resume.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on routing key resume.generated. Two consumers, each on its own queue:
 * prs-service activates the previously-reserved 10% resume slot, and student-service fills in
 * StudentProfile.resumeUrl (worth 15% of profile completion, which nothing wrote before this).
 *
 * atsScore is a Double on the 0-100 scale, identical to how prs-service already stores
 * assessmentScore/roadmapScore/profileScore -- it becomes resumeScore verbatim, no conversion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeGeneratedEvent {

    private Long resumeId;
    private Long studentId;
    private Double atsScore;
    private Integer version;
    private LocalDateTime generatedAt;
}
