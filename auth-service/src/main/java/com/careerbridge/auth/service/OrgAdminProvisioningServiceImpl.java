package com.careerbridge.auth.service;

import com.careerbridge.auth.config.RabbitMQConfig;
import com.careerbridge.auth.event.OrgAdminInvitedEvent;
import com.careerbridge.auth.event.OrganizationRequestApprovedEvent;
import com.careerbridge.auth.event.StudentRegisteredEvent;
import com.careerbridge.auth.model.PasswordResetOtp;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.PasswordResetOtpRepository;
import com.careerbridge.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns an approved institution application into a working ORG_ADMIN login, by reusing the exact
 * activation machinery already built for password reset -- see PasswordResetOtp and
 * AuthServiceImpl.resetPassword. There is deliberately no separate "activation token" table or
 * endpoint: the TPO's invite link posts to the already-public POST /api/auth/forgot-password/reset.
 */
@Service
public class OrgAdminProvisioningServiceImpl implements OrgAdminProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(OrgAdminProvisioningServiceImpl.class);

    private static final int INVITE_EXPIRY_HOURS = 24;

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;

    public OrgAdminProvisioningServiceImpl(UserRepository userRepository,
                                           PasswordResetOtpRepository passwordResetOtpRepository,
                                           PasswordEncoder passwordEncoder,
                                           RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.passwordEncoder = passwordEncoder;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public void provision(OrganizationRequestApprovedEvent event) {
        boolean isNewUser = false;
        Optional<User> existing = userRepository.findByEmail(event.getAdminEmail());
        User user;

        if (existing.isPresent()) {
            user = existing.get();
            user.setRole(Role.ORG_ADMIN);
            user.setOrganizationId(event.getOrganizationId());
            user.setIsDeleted(false);
            user = userRepository.save(user);
        } else {
            isNewUser = true;
            String[] nameParts = splitName(event.getAdminName());
            user = userRepository.save(User.builder()
                    .firstName(nameParts[0])
                    .lastName(nameParts[1])
                    .email(event.getAdminEmail())
                    // A random password nobody, including this service, ever learns in plaintext.
                    // The account is only ever reachable through the activation link below.
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(Role.ORG_ADMIN)
                    .organizationId(event.getOrganizationId())
                    .build());
        }

        String resetToken = issueOrReuseActivationToken(user.getId());

        publishOrgAdminInvited(user, event.getOrganizationName(), resetToken);
        if (isNewUser) {
            publishStudentRegistered(user);
        }
    }

    /**
     * RabbitMQ is at-least-once: a redelivery of the same approval event must not leave the TPO
     * with two separately-working links in their inbox. Reusing the newest unused, unexpired row
     * (the same "newest row is the only one that can ever be valid" rule PasswordResetOtp's own
     * javadoc documents) makes a redelivery a no-op rather than a second grant.
     */
    private String issueOrReuseActivationToken(Long userId) {
        Optional<PasswordResetOtp> reusable = passwordResetOtpRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(otp -> !otp.getUsed())
                .filter(otp -> otp.getExpiresAt().isAfter(LocalDateTime.now()))
                .filter(otp -> otp.getResetToken() != null);

        if (reusable.isPresent()) {
            return reusable.get().getResetToken();
        }

        String resetToken = UUID.randomUUID().toString();
        passwordResetOtpRepository.save(PasswordResetOtp.builder()
                .userId(userId)
                // otpHash hashes a value nobody ever learns -- this row is only ever redeemed via
                // resetToken below, never through the OTP-entry path forgotPassword/verifyOtp use.
                .otpHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .resetToken(resetToken)
                .expiresAt(LocalDateTime.now().plusHours(INVITE_EXPIRY_HOURS))
                .build());

        return resetToken;
    }

    private static String[] splitName(String adminName) {
        if (adminName == null || adminName.isBlank()) {
            return new String[] {"there", ""};
        }
        String trimmed = adminName.trim();
        int spaceIndex = trimmed.indexOf(' ');
        return spaceIndex < 0
                ? new String[] {trimmed, ""}
                : new String[] {trimmed.substring(0, spaceIndex), trimmed.substring(spaceIndex + 1).trim()};
    }

    /** Fail-soft, same reasoning as every other publisher in this service. */
    private void publishOrgAdminInvited(User user, String organizationName, String resetToken) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ORG_ADMIN_INVITED_ROUTING_KEY,
                    OrgAdminInvitedEvent.builder()
                            .email(user.getEmail())
                            .firstName(user.getFirstName())
                            .organizationName(organizationName)
                            .resetToken(resetToken)
                            .expiresInHours(INVITE_EXPIRY_HOURS)
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for userId={}: {}",
                    RabbitMQConfig.ORG_ADMIN_INVITED_ROUTING_KEY, user.getId(), ex.getMessage());
        }
    }

    /** Same shape as AuthServiceImpl.publishStudentRegistered -- gives the new admin a contact row too. */
    private void publishStudentRegistered(User user) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.STUDENT_REGISTERED_ROUTING_KEY,
                    StudentRegisteredEvent.builder()
                            .userId(user.getId())
                            .email(user.getEmail())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .role(user.getRole())
                            .organizationId(user.getOrganizationId())
                            .registeredAt(LocalDateTime.now())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for userId={}: {}",
                    RabbitMQConfig.STUDENT_REGISTERED_ROUTING_KEY, user.getId(), ex.getMessage());
        }
    }
}
