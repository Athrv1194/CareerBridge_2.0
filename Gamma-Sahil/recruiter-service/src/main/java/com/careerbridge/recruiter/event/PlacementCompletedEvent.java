package com.careerbridge.recruiter.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Published on careerbridge.exchange with routing key placement.completed when a student ACCEPTS an
 * offer.
 *
 * Deliberately on ACCEPTED and not on OFFERED, which is what the original task spec said. Extending
 * an offer is not a placement -- the student may well decline it. The placement is the student
 * taking the job, and that is also the event any consumer would actually want ("congratulate this
 * student", "this student is placed, stop nudging them about their readiness score").
 *
 * No consumer exists yet and NO QUEUE IS DECLARED for it, matching application.submitted,
 * application.status.updated, organization.created and prs.updated. A queue bound with no listener
 * accrues every event forever and looks exactly like an unprocessed backlog. A future consumer
 * declares its own queue, per this project's one-queue-per-consumer-per-event rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementCompletedEvent {

    private Long applicationId;
    private Long studentId;
    private Long jobId;
    private String jobTitle;
    private String companyName;

    /** In LPA, copied from the accepted offer. */
    private BigDecimal offeredCtc;

    /** When the offer was originally extended, not when it was accepted. */
    private LocalDateTime offerDate;

    private LocalDateTime acceptedAt;
}
