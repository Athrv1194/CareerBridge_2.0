package com.careerbridge.auth.service;

import com.careerbridge.auth.config.JwtConfig;
import com.careerbridge.auth.config.RabbitMQConfig;
import com.careerbridge.auth.dto.AuthResponse;
import com.careerbridge.auth.dto.LoginRequest;
import com.careerbridge.auth.dto.RegisterRequest;
import com.careerbridge.auth.event.StudentRegisteredEvent;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.model.RefreshToken;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.RefreshTokenRepository;
import com.careerbridge.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** Same message for unknown email, wrong password and soft-deleted account: never leak which. */
    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtConfig jwtConfig;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           JwtConfig jwtConfig,
                           PasswordEncoder passwordEncoder,
                           RabbitTemplate rabbitTemplate) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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

    /**
     * Fail-soft by design: a RabbitMQ outage must not cost the user their registration.
     *
     * ponytail: publishes before the surrounding transaction commits, so a rollback after this
     * point would leave a phantom event. Move to @TransactionalEventListener(AFTER_COMMIT) if
     * exactly-once delivery starts mattering.
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
}
