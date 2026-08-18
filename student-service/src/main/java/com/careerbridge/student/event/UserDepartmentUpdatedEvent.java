package com.careerbridge.student.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * This service's own copy of auth-service's user.department.updated payload.
 *
 * A separate class in this package, not a shared module -- there is no shared library anywhere in
 * this project, and the @RabbitListener parameter must stay this concrete type so Spring AMQP's
 * default TypePrecedence.INFERRED resolves the payload from the method signature rather than the
 * sender's __TypeId__ header, which names auth-service's package and would not resolve here.
 *
 * department is nullable and null is MEANINGFUL: it is an instruction to clear, not an absent
 * field. See the consumer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDepartmentUpdatedEvent {

    private Long userId;

    private String department;

    private Long organizationId;

    private LocalDateTime updatedAt;
}
