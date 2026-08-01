package com.careerbridge.prs.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on careerbridge.exchange with routing key "prs.updated" every time a student's score is
 * recomputed.
 *
 * Nothing consumes it yet -- the intended consumer is a future dashboard service. No queue is bound
 * to this key, so these messages are currently discarded by the exchange. That is expected, not a
 * fault: verify publication through the RabbitMQ console or a temporary durable probe queue, never
 * by looking for a downstream effect. A future consumer declares its own queue and binding.
 *
 * Carries the derived figures only, not the three raw inputs. A dashboard wants "student 5 is now a
 * B at 62.4"; anything needing the breakdown can call GET /api/prs/{studentId}, and keeping the
 * payload thin means adding an input later does not change this contract.
 *
 * Flat types only, so adding a field here can never hard-fail a consumer holding an older copy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrsUpdatedEvent {

    private Long studentId;
    private Double totalScore;
    private String grade;
    private LocalDateTime updatedAt;
}
