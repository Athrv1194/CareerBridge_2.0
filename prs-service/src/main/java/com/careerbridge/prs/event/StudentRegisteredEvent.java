package com.careerbridge.prs.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * This service's own copy of what auth-service publishes on careerbridge.exchange with routing key
 * "student.registered" -- NOT "user.registered", which no publisher in this system emits.
 *
 * role is a String here, never auth-service's Role enum, even though the publisher declares an
 * enum. Wire-identical, but a duplicated enum makes Jackson hard-fail every event the day
 * auth-service adds a value this copy has not been taught about. student-service's copy makes the
 * same choice for the same reason; see CLAUDE.md.
 *
 * organizationId is the field that makes the tenant-scoped leaderboard possible -- it is harvested
 * into PlacementReadinessScore.organizationId on initialisation. It is legitimately null for a
 * SUPER_ADMIN.
 *
 * This is the third consumer of this event, after student-service (creates the profile) and
 * notification-service (harvests contact details). Each holds its own copy and its own queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRegisteredEvent {

    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private Long organizationId;
    private LocalDateTime registeredAt;
}
