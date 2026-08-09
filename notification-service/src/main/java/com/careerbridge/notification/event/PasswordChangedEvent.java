package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side copy of the payload auth-service publishes after a successful password reset
 * (exchange careerbridge.exchange, routing key password.changed). Drives the "if this wasn't you"
 * confirmation email -- the one part of this flow that fires whether or not the reset was
 * legitimate, since its whole purpose is letting the real owner notice an illegitimate one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangedEvent {

    private String email;
    private String firstName;
    private LocalDateTime changedAt;
}
