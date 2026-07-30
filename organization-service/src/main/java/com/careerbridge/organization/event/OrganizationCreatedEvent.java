package com.careerbridge.organization.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published to careerbridge.exchange with routing key organization.created.
 *
 * Nothing consumes this yet. It is published anyway so the tenant lifecycle is on the bus from day
 * one; a future consumer declares its own queue and binding, which is why this service declares
 * neither. Adding a field stays backward compatible with any consumer holding an older copy
 * (Jackson 3 has FAIL_ON_UNKNOWN_PROPERTIES off); removing or renaming one does not.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationCreatedEvent {

    private Long organizationId;

    private String name;

    private String type;

    private String contactEmail;

    private LocalDateTime createdAt;
}
