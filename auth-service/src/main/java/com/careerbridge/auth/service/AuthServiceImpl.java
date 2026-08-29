package com.careerbridge.auth.service;

import com.careerbridge.auth.config.JwtConfig;
import com.careerbridge.auth.config.RabbitMQConfig;
import com.careerbridge.auth.dto.AuthResponse;
import com.careerbridge.auth.dto.ForgotPasswordRequest;
import com.careerbridge.auth.dto.LoginRequest;
import com.careerbridge.auth.dto.RegisterRequest;
import com.careerbridge.auth.dto.ResetPasswordRequest;
import com.careerbridge.auth.dto.VerifyOtpRequest;
import com.careerbridge.auth.dto.VerifyOtpResponse;
import com.careerbridge.auth.event.PasswordChangedEvent;
import com.careerbridge.auth.event.PasswordResetRequestedEvent;
import com.careerbridge.auth.event.StudentRegisteredEvent;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.model.PasswordResetOtp;
import com.careerbridge.auth.model.RefreshToken;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.PasswordResetOtpRepository;
import com.careerbridge.auth.repository.RefreshTokenRepository;
import com.careerbridge.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** Same message for unknown email, wrong password and soft-deleted account: never leak which. */
    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private static final String INVALID_OR_EXPIRED_CODE = "Invalid or expired code";
    private static final String INVALID_OR_EXPIRED_RESET_LINK = "That reset link is invalid or has expired";

    private static final int OTP_LENGTH = 4;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 30;
    private static final int OTP_MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final JwtConfig jwtConfig;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordResetOtpRepository passwordResetOtpRepository,
                           JwtConfig jwtConfig,
                           PasswordEncoder passwordEncoder,
                           RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.jwtConfig = jwtConfig;
        this.passwordEncoder = passwordEncoder;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (Boolean.TRUE.equals(userRepository.existsByEmail(request.getEmail()))) {
            throw new CustomException("Email is already registered", HttpStatus.CONFLICT);
        }

        User user = userRepository.save(User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                // explicit null in the JSON body beats the DTO field initializer, so re-default here
                .role(request.getRole() == null ? Role.STUDENT : request.getRole())
                .organizationId(request.getOrganizationId())
                .build());

        AuthResponse response = issueTokens(user);
        publishStudentRegistered(user);
        return response;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED));

        requireActive(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (Boolean.TRUE.equals(stored.getIsRevoked())) {
            throw new CustomException("Refresh token has been revoked", HttpStatus.UNAUTHORIZED);
        }
        if (stored.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new CustomException("Refresh token has expired", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new CustomException(BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED));

        requireActive(user);

        // Same refresh token is returned: it stays valid until it expires or is revoked.
        return buildResponse(user, jwtConfig.generateAccessToken(user), stored.getToken());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        stored.setIsRevoked(true);
        refreshTokenRepository.save(stored);
    }

    /**
     * Silent no-op for an unknown email -- returning the same generic outcome either way is what
     * stops this endpoint being usable to enumerate registered accounts. The cooldown check only
     * runs once we already know the user exists, so it never fires (and never differs) for an
     * email that was never registered.
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> maybeUser = userRepository.findByEmail(request.getEmail());
        if (maybeUser.isEmpty() || Boolean.TRUE.equals(maybeUser.get().getIsDeleted())) {
            return;
        }
        User user = maybeUser.get();

        passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .filter(otp -> !otp.getUsed())
                .filter(otp -> otp.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(OTP_RESEND_COOLDOWN_SECONDS)))
                .ifPresent(otp -> {
                    long waited = ChronoUnit.SECONDS.between(otp.getCreatedAt(), LocalDateTime.now());
                    throw new CustomException(
                            "Please wait " + (OTP_RESEND_COOLDOWN_SECONDS - waited) + "s before requesting another code",
                            HttpStatus.TOO_MANY_REQUESTS);
                });

        String otp = generateOtp();
        passwordResetOtpRepository.save(PasswordResetOtp.builder()
                .userId(user.getId())
                .otpHash(passwordEncoder.encode(otp))
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build());

        publishPasswordResetRequested(user, otp);
    }

    @Override
    @Transactional
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(INVALID_OR_EXPIRED_CODE, HttpStatus.BAD_REQUEST));

        PasswordResetOtp record = passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new CustomException(INVALID_OR_EXPIRED_CODE, HttpStatus.BAD_REQUEST));

        // A code that has already completed a reset, expired, or been guessed OTP_MAX_ATTEMPTS
        // times is dead -- the only way forward from any of these is a fresh forgotPassword call.
        if (Boolean.TRUE.equals(record.getUsed())
                || record.getExpiresAt().isBefore(LocalDateTime.now())
                || record.getAttempts() >= OTP_MAX_ATTEMPTS) {
            throw new CustomException(INVALID_OR_EXPIRED_CODE, HttpStatus.BAD_REQUEST);
        }

        if (!passwordEncoder.matches(request.getOtp(), record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            passwordResetOtpRepository.save(record);
            throw new CustomException(INVALID_OR_EXPIRED_CODE, HttpStatus.BAD_REQUEST);
        }

        record.setResetToken(java.util.UUID.randomUUID().toString());
        passwordResetOtpRepository.save(record);

        return VerifyOtpResponse.builder().resetToken(record.getResetToken()).build();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException("Passwords do not match", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(INVALID_OR_EXPIRED_RESET_LINK, HttpStatus.BAD_REQUEST));

        PasswordResetOtp record = passwordResetOtpRepository.findByResetTokenAndUsedFalse(request.getResetToken())
                .filter(otp -> otp.getUserId().equals(user.getId()))
                .orElseThrow(() -> new CustomException(INVALID_OR_EXPIRED_RESET_LINK, HttpStatus.BAD_REQUEST));

        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CustomException(INVALID_OR_EXPIRED_RESET_LINK, HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        record.setUsed(true);
        passwordResetOtpRepository.save(record);

        // Every other refresh token this user holds should die with the password -- otherwise a
        // session started before the reset (e.g. on a device that leaked the old password) stays
        // valid forever, which defeats the point of resetting it.
        refreshTokenRepository.deleteByUserId(user.getId());

        publishPasswordChanged(user);
    }

    /**
     * Single soft-delete gate for every authentication path. Keeping it here rather than in
     * the repository query means a new caller cannot forget to filter on isDeleted.
     */
    private void requireActive(User user) {
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new CustomException(BAD_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }
    }

    private AuthResponse issueTokens(User user) {
        String refreshToken = jwtConfig.generateRefreshToken();

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .userId(user.getId())
                .expiryDate(jwtConfig.getRefreshTokenExpiryDate())
                .build());

        return buildResponse(user, jwtConfig.generateAccessToken(user), refreshToken);
    }

    private AuthResponse buildResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .organizationId(user.getOrganizationId())
                .build();
    }

    /** SecureRandom, not Math.random() -- this code gates a password reset. */
    private String generateOtp() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        int value = secureRandom.nextInt(bound);
        return String.format("%0" + OTP_LENGTH + "d", value);
    }

    /**
     * Fail-soft by design: a RabbitMQ outage must not cost the user their registration.
     *
     * Publishes before the surrounding transaction commits, so a rollback after this point would
     * leave a phantom event. Move to @TransactionalEventListener(AFTER_COMMIT) if exactly-once
     * delivery starts mattering.
     */
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

    /** Same fail-soft reasoning as publishStudentRegistered -- a broker outage must not 500 this call. */
    private void publishPasswordResetRequested(User user, String otp) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.PASSWORD_RESET_REQUESTED_ROUTING_KEY,
                    PasswordResetRequestedEvent.builder()
                            .email(user.getEmail())
                            .firstName(user.getFirstName())
                            .otp(otp)
                            .expiresInMinutes(OTP_EXPIRY_MINUTES)
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for userId={}: {}",
                    RabbitMQConfig.PASSWORD_RESET_REQUESTED_ROUTING_KEY, user.getId(), ex.getMessage());
        }
    }

    private void publishPasswordChanged(User user) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.PASSWORD_CHANGED_ROUTING_KEY,
                    PasswordChangedEvent.builder()
                            .email(user.getEmail())
                            .firstName(user.getFirstName())
                            .changedAt(LocalDateTime.now())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for userId={}: {}",
                    RabbitMQConfig.PASSWORD_CHANGED_ROUTING_KEY, user.getId(), ex.getMessage());
        }
    }
}
