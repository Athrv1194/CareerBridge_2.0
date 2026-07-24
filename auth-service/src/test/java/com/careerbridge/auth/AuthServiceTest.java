package com.careerbridge.auth;

import com.careerbridge.auth.config.JwtConfig;
import com.careerbridge.auth.dto.AuthResponse;
import com.careerbridge.auth.dto.LoginRequest;
import com.careerbridge.auth.dto.RegisterRequest;
import com.careerbridge.auth.exception.CustomException;
import com.careerbridge.auth.model.RefreshToken;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
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
}
