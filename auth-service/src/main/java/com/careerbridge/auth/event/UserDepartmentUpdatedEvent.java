package com.careerbridge.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payload published on careerbridge.exchange with routing key user.department.updated, whenever a
 * user's department is set, changed or CLEARED.
 *
 * department is deliberately nullable: clearing one is a real transition consumers must see, and a
 * cleared department has to reach student-service or its local copy would keep a value auth-service
 * no longer holds. A consumer must therefore treat null as "unassign", never as "no data, skip".
 *
 * Carries the ABSOLUTE current value, never a delta -- RabbitMQ is at-least-once, so a redelivery
 * simply re-applies the same assignment. Same rule as SessionCompletedEvent's absolute session
 * count and SubscriptionActivatedEvent's absolute validUntil.
 *
 * Flat types only, no enums, matching every other event in this project: a consumer holding an
 * older copy of this class must not hard-fail on a value it does not know.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDepartmentUpdatedEvent {

    private Long userId;

    /** Null means the user now has NO department -- an instruction to clear, not an absent field. */
    private String department;

    private Long organizationId;

    private LocalDateTime updatedAt;
}
