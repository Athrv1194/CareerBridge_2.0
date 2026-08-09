package com.careerbridge.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published after a successful password reset, never after a normal logged-in password change
 * (there is no such endpoint yet) -- so today this only ever follows the forgot-password flow.
 * Consumed by notification-service to send the "if this wasn't you" confirmation email.
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
