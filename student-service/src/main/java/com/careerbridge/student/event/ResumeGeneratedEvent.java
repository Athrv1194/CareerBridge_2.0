package com.careerbridge.student.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side copy of the payload resume-service publishes on generation
 * (exchange careerbridge.exchange, routing key resume.generated).
 *
 * Field names and JSON shape must stay in lockstep with
 * com.careerbridge.resume.event.ResumeGeneratedEvent. The differing package is fine: the listener
 * method takes this concrete type, and Spring AMQP's default TypePrecedence.INFERRED resolves the
 * payload from the method signature without ever reading the sender's __TypeId__ header.
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
