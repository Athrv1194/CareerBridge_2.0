package com.careerbridge.auth;

import com.careerbridge.auth.config.JwtConfig;
import com.careerbridge.auth.dto.AuthResponse;
import com.careerbridge.auth.dto.ForgotPasswordRequest;
import com.careerbridge.auth.dto.LoginRequest;
import com.careerbridge.auth.dto.RegisterRequest;
import com.careerbridge.auth.dto.ResetPasswordRequest;
import com.careerbridge.auth.dto.VerifyOtpRequest;
import com.careerbridge.auth.dto.VerifyOtpResponse;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.model.PasswordResetOtp;
import com.careerbridge.auth.model.RefreshToken;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.PasswordResetOtpRepository;
import com.careerbridge.auth.repository.RefreshTokenRepository;
import com.careerbridge.auth.repository.UserRepository;
import com.careerbridge.auth.service.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetOtpRepository passwordResetOtpRepository;
    @Mock private JwtConfig jwtConfig;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private AuthServiceImpl authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("Ada");
        registerRequest.setLastName("Lovelace");
        registerRequest.setEmail("ada@careerbridge.com");
        registerRequest.setPassword("plaintext-password");
        registerRequest.setRole(Role.STUDENT);
        registerRequest.setOrganizationId(7L);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("ada@careerbridge.com");
        loginRequest.setPassword("plaintext-password");
    }

    private User persistedUser() {
        return User.builder()
                .id(1L)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@careerbridge.com")
                .password("hashed-password")
                .role(Role.STUDENT)
                .organizationId(7L)
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("register: hashes the password, saves the user, and returns both tokens")
    void registerUser_Success() {
        when(userRepository.existsByEmail("ada@careerbridge.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(persistedUser());
        when(jwtConfig.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtConfig.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtConfig.getRefreshTokenExpiryDate()).thenReturn(LocalDateTime.now().plusDays(7));

        AuthResponse response = authService.register(registerRequest);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(1L, response.getUserId());
        assertEquals("ada@careerbridge.com", response.getEmail());
        assertEquals(Role.STUDENT, response.getRole());
        assertEquals(7L, response.getOrganizationId());

        // the raw password must never reach the database
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("hashed-password", saved.getValue().getPassword());

        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("register: duplicate email is rejected with 409 and nothing is persisted")
    void registerUser_EmailAlreadyExists_ThrowsException() {
        when(userRepository.existsByEmail("ada@careerbridge.com")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.register(registerRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("login: valid credentials return tokens and persist the refresh token")
    void login_ValidCredentials_ReturnsTokens() {
        when(userRepository.findByEmail("ada@careerbridge.com"))
                .thenReturn(Optional.of(persistedUser()));
        when(passwordEncoder.matches("plaintext-password", "hashed-password")).thenReturn(true);
        when(jwtConfig.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtConfig.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtConfig.getRefreshTokenExpiryDate()).thenReturn(LocalDateTime.now().plusDays(7));

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(1L, response.getUserId());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("login: wrong password gives 401 and issues no token")
    void login_InvalidPassword_ThrowsException() {
        when(userRepository.findByEmail("ada@careerbridge.com"))
                .thenReturn(Optional.of(persistedUser()));
        when(passwordEncoder.matches("plaintext-password", "hashed-password")).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.login(loginRequest));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        // message must not reveal whether it was the email or the password that was wrong
        assertEquals("Invalid email or password", ex.getMessage());
        verify(jwtConfig, never()).generateAccessToken(any(User.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refresh: a live token yields a new access token and reuses the refresh token")
    void refreshToken_ValidToken_ReturnsNewAccessToken() {
        RefreshToken stored = RefreshToken.builder()
                .id(10L)
                .token("refresh-token")
                .userId(1L)
                .expiryDate(LocalDateTime.now().plusDays(3))
                .isRevoked(false)
                .build();

        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(persistedUser()));
        when(jwtConfig.generateAccessToken(any(User.class))).thenReturn("new-access-token");

        AuthResponse response = authService.refreshToken("refresh-token");

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(1L, response.getUserId());
        // reuse, not rotation: no new refresh token row is written
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(jwtConfig, never()).generateRefreshToken();
    }

    @Test
    @DisplayName("refresh: the new token is minted from a freshly loaded User, so a plan change lands")
    void refreshToken_AfterPlanChange_MintsFromFreshlyLoadedUser() {
        // The keystone of the payment-service subscription chain. payment-service publishes
        // subscription.activated, this service's consumer writes the new plan onto the User row,
        // and the student then refreshes. That only surfaces the new plan if refreshToken re-reads
        // the user from the database rather than trusting anything cached or carried in the old
        // token. If this ever regresses to reusing stale state, a paid student would keep the FREE
        // claim forever.
        RefreshToken stored = RefreshToken.builder()
                .id(10L)
                .token("refresh-token")
                .userId(1L)
                .expiryDate(LocalDateTime.now().plusDays(3))
                .isRevoked(false)
                .build();

        User upgraded = persistedUser();
        upgraded.setSubscriptionPlan("STUDENT_PREMIUM");
        upgraded.setSubscriptionExpiry(LocalDateTime.now().plusDays(30));

        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(upgraded));
        when(jwtConfig.generateAccessToken(any(User.class))).thenReturn("new-access-token");

        authService.refreshToken("refresh-token");

        // The entity handed to the token builder must be the one just loaded, carrying the new plan.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(jwtConfig).generateAccessToken(captor.capture());
        assertEquals("STUDENT_PREMIUM", captor.getValue().getSubscriptionPlan());
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("login: soft-deleted account is refused even with the correct password")
    void login_SoftDeletedUser_ThrowsException() {
        User deleted = persistedUser();
        deleted.setIsDeleted(true);
        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(deleted));

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.login(loginRequest));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtConfig, never()).generateAccessToken(any(User.class));
    }

    @Test
    @DisplayName("refresh: a revoked token is refused, so logout genuinely ends the session")
    void refreshToken_RevokedToken_ThrowsException() {
        RefreshToken revoked = RefreshToken.builder()
                .id(10L)
                .token("refresh-token")
                .userId(1L)
                .expiryDate(LocalDateTime.now().plusDays(3))
                .isRevoked(true)
                .build();

        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(revoked));

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.refreshToken("refresh-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(jwtConfig, never()).generateAccessToken(any(User.class));
    }

    @Test
    @DisplayName("register: a RabbitMQ outage must not cost the user their account")
    void registerUser_BrokerDown_StillSucceeds() {
        when(userRepository.existsByEmail("ada@careerbridge.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(persistedUser());
        when(jwtConfig.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtConfig.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtConfig.getRefreshTokenExpiryDate()).thenReturn(LocalDateTime.now().plusDays(7));
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpConnectException(new RuntimeException("broker down")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        AuthResponse response = authService.register(registerRequest);

        assertEquals("access-token", response.getAccessToken());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("logout: revokes the stored refresh token")
    void logout_ValidToken_RevokesIt() {
        RefreshToken stored = RefreshToken.builder()
                .id(10L)
                .token("refresh-token")
                .userId(1L)
                .expiryDate(LocalDateTime.now().plusDays(3))
                .isRevoked(false)
                .build();

        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(stored));

        authService.logout("refresh-token");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertEquals(Boolean.TRUE, saved.getValue().getIsRevoked());
    }

    @Test
    @DisplayName("register: an explicit null role falls back to STUDENT")
    void registerUser_NullRole_DefaultsToStudent() {
        registerRequest.setRole(null);
        when(userRepository.existsByEmail("ada@careerbridge.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(persistedUser());
        when(jwtConfig.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtConfig.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtConfig.getRefreshTokenExpiryDate()).thenReturn(LocalDateTime.now().plusDays(7));

        authService.register(registerRequest);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.STUDENT, saved.getValue().getRole());
        verify(rabbitTemplate).convertAndSend(anyString(), eq("student.registered"), any(Object.class));
    }

    // ---------------------------------------------------------------------------------------------
    // ADMIN MODULE: a user deactivated through /api/auth/admin/users/{id}/deactivate must not be
    // able to authenticate. Both tests below pin behaviour that already existed before the admin
    // module -- they exist because the admin endpoints are what make deactivation reachable, and
    // nothing was covering the gate.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("login: a deactivated user is refused with the same generic 401 as a bad password")
    void login_DeactivatedUser_Throws401() {
        User deactivated = persistedUser();
        deactivated.setIsDeleted(true);
        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(deactivated));

        CustomException ex = assertThrows(CustomException.class, () -> authService.login(loginRequest));

        // 401 and "Invalid email or password", NOT a distinct 403 "account deactivated". A specific
        // message would turn login into a user-enumeration oracle: an attacker could learn both that
        // an email exists and that it is deactivated. Deliberate -- do not "improve" this message.
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Invalid email or password", ex.getMessage());

        // The gate runs before the password is even checked, so a deactivated account cannot be used
        // as a password oracle either.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtConfig, never()).generateAccessToken(any(User.class));
    }

    @Test
    @DisplayName("refresh: a deactivated user's existing refresh token stops working too")
    void refreshToken_DeactivatedUser_Throws401() {
        // Without this the deactivation would only take effect when the access token expired, and a
        // valid refresh token would keep minting new ones for up to its full lifetime.
        User deactivated = persistedUser();
        deactivated.setIsDeleted(true);
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(
                RefreshToken.builder()
                        .token("refresh-token")
                        .userId(1L)
                        .isRevoked(false)
                        .expiryDate(LocalDateTime.now().plusDays(7))
                        .build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(deactivated));

        CustomException ex = assertThrows(CustomException.class,
                () -> authService.refreshToken("refresh-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(jwtConfig, never()).generateAccessToken(any(User.class));
    }

    // ---------------------------------------------------------------------------------------------
    // FORGOT PASSWORD
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("forgotPassword: unknown email is a silent no-op, never revealing the account doesn't exist")
    void forgotPassword_UnknownEmail_DoesNothing() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("nobody@careerbridge.com");
        when(userRepository.findByEmail("nobody@careerbridge.com")).thenReturn(Optional.empty());

        authService.forgotPassword(request);

        verify(passwordResetOtpRepository, never()).save(any(PasswordResetOtp.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("forgotPassword: a known email gets a hashed OTP saved and an event published")
    void forgotPassword_KnownEmail_SavesOtpAndPublishesEvent() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("ada@careerbridge.com");
        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-otp");

        authService.forgotPassword(request);

        ArgumentCaptor<PasswordResetOtp> saved = ArgumentCaptor.forClass(PasswordResetOtp.class);
        verify(passwordResetOtpRepository).save(saved.capture());
        assertEquals(1L, saved.getValue().getUserId());
        assertEquals("hashed-otp", saved.getValue().getOtpHash());
        // the plaintext code must never be logged or stored, only carried in the outbound event
        verify(rabbitTemplate).convertAndSend(anyString(), eq("password.reset.requested"), any(Object.class));
    }

    @Test
    @DisplayName("forgotPassword: a resend within the cooldown window is rejected with 429")
    void forgotPassword_WithinCooldown_Throws429() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("ada@careerbridge.com");
        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(
                PasswordResetOtp.builder()
                        .id(5L).userId(1L).otpHash("hashed-otp").used(false)
                        .expiresAt(LocalDateTime.now().plusMinutes(9))
                        .createdAt(LocalDateTime.now().minusSeconds(5))
                        .build()));

        CustomException ex = assertThrows(CustomException.class, () -> authService.forgotPassword(request));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        verify(passwordResetOtpRepository, never()).save(any(PasswordResetOtp.class));
    }

    @Test
    @DisplayName("forgotPassword: an already-used OTP row does not block a fresh resend")
    void forgotPassword_LastOtpAlreadyUsed_AllowsNewCode() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("ada@careerbridge.com");
        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(
                PasswordResetOtp.builder()
                        .id(5L).userId(1L).otpHash("old-hash").used(true)
                        .expiresAt(LocalDateTime.now().plusMinutes(9))
                        .createdAt(LocalDateTime.now().minusSeconds(5))
                        .build()));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-otp");

        authService.forgotPassword(request);

        verify(passwordResetOtpRepository).save(any(PasswordResetOtp.class));
    }

    @Test
    @DisplayName("verifyOtp: correct code returns a reset token and marks the row with it")
    void verifyOtp_CorrectCode_ReturnsResetToken() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("ada@careerbridge.com");
        request.setOtp("1234");
        PasswordResetOtp record = PasswordResetOtp.builder()
                .id(5L).userId(1L).otpHash("hashed-otp").used(false).attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(9))
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(record));
        when(passwordEncoder.matches("1234", "hashed-otp")).thenReturn(true);

        VerifyOtpResponse response = authService.verifyOtp(request);

        assertNotNull(response.getResetToken());
        assertEquals(response.getResetToken(), record.getResetToken());
        verify(passwordResetOtpRepository).save(record);
    }

    @Test
    @DisplayName("verifyOtp: wrong code is rejected and counts against the attempt limit")
    void verifyOtp_WrongCode_IncrementsAttemptsAndThrows() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("ada@careerbridge.com");
        request.setOtp("0000");
        PasswordResetOtp record = PasswordResetOtp.builder()
                .id(5L).userId(1L).otpHash("hashed-otp").used(false).attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(9))
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(record));
        when(passwordEncoder.matches("0000", "hashed-otp")).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class, () -> authService.verifyOtp(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(1, record.getAttempts());
        verify(passwordResetOtpRepository).save(record);
    }

    @Test
    @DisplayName("verifyOtp: a code that has already hit the attempt cap is dead even if now correct")
    void verifyOtp_AttemptsExhausted_ThrowsWithoutCheckingHash() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("ada@careerbridge.com");
        request.setOtp("1234");
        PasswordResetOtp record = PasswordResetOtp.builder()
                .id(5L).userId(1L).otpHash("hashed-otp").used(false).attempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(9))
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(record));

        CustomException ex = assertThrows(CustomException.class, () -> authService.verifyOtp(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("verifyOtp: an expired code is rejected even if it would otherwise match")
    void verifyOtp_Expired_Throws() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("ada@careerbridge.com");
        request.setOtp("1234");
        PasswordResetOtp record = PasswordResetOtp.builder()
                .id(5L).userId(1L).otpHash("hashed-otp").used(false).attempts(0)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(11))
                .build();

        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(record));

        CustomException ex = assertThrows(CustomException.class, () -> authService.verifyOtp(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("resetPassword: mismatched confirmation is rejected before touching the database")
    void resetPassword_PasswordsDoNotMatch_Throws() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("ada@careerbridge.com");
        request.setResetToken("token-123");
        request.setNewPassword("newPassword1");
        request.setConfirmPassword("different1");

        CustomException ex = assertThrows(CustomException.class, () -> authService.resetPassword(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("resetPassword: valid token updates the password, revokes sessions, and notifies")
    void resetPassword_ValidToken_UpdatesPasswordAndRevokesSessions() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("ada@careerbridge.com");
        request.setResetToken("token-123");
        request.setNewPassword("newPassword1");
        request.setConfirmPassword("newPassword1");

        PasswordResetOtp record = PasswordResetOtp.builder()
                .id(5L).userId(1L).otpHash("hashed-otp").used(false)
                .resetToken("token-123")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findByResetTokenAndUsedFalse("token-123")).thenReturn(Optional.of(record));
        when(passwordEncoder.encode("newPassword1")).thenReturn("new-hashed-password");

        authService.resetPassword(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals("new-hashed-password", savedUser.getValue().getPassword());

        assertEquals(Boolean.TRUE, record.getUsed());
        verify(passwordResetOtpRepository).save(record);
        verify(refreshTokenRepository).deleteByUserId(1L);
        verify(rabbitTemplate).convertAndSend(anyString(), eq("password.changed"), any(Object.class));
    }

    @Test
    @DisplayName("resetPassword: a token belonging to a different user is refused")
    void resetPassword_TokenBelongsToDifferentUser_Throws() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("ada@careerbridge.com");
        request.setResetToken("token-123");
        request.setNewPassword("newPassword1");
        request.setConfirmPassword("newPassword1");

        PasswordResetOtp record = PasswordResetOtp.builder()
                .id(5L).userId(99L).otpHash("hashed-otp").used(false)
                .resetToken("token-123")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("ada@careerbridge.com")).thenReturn(Optional.of(persistedUser()));
        when(passwordResetOtpRepository.findByResetTokenAndUsedFalse("token-123")).thenReturn(Optional.of(record));

        CustomException ex = assertThrows(CustomException.class, () -> authService.resetPassword(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }
}
