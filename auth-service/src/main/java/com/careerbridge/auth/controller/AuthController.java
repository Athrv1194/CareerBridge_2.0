package com.careerbridge.auth.controller;

import com.careerbridge.auth.dto.AuthResponse;
import com.careerbridge.auth.dto.ForgotPasswordRequest;
import com.careerbridge.auth.dto.LoginRequest;
import com.careerbridge.auth.dto.MessageResponse;
import com.careerbridge.auth.dto.RefreshTokenRequest;
import com.careerbridge.auth.dto.RegisterRequest;
import com.careerbridge.auth.dto.ResetPasswordRequest;
import com.careerbridge.auth.dto.VerifyOtpRequest;
import com.careerbridge.auth.dto.VerifyOtpResponse;
import com.careerbridge.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Same generic outcome text regardless of what forgotPassword actually did internally --
    // see AuthServiceImpl.forgotPassword's javadoc on why that must never vary by email.
    private static final String FORGOT_PASSWORD_GENERIC_MESSAGE =
            "If that email is registered, a verification code has been sent.";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(MessageResponse.builder().message(FORGOT_PASSWORD_GENERIC_MESSAGE).build());
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(MessageResponse.builder().message("Your password has been changed.").build());
    }
}
