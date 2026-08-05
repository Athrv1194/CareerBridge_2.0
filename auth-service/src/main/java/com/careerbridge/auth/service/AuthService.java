package com.careerbridge.auth.service;

import com.careerbridge.auth.dto.AuthResponse;
import com.careerbridge.auth.dto.ForgotPasswordRequest;
import com.careerbridge.auth.dto.LoginRequest;
import com.careerbridge.auth.dto.RegisterRequest;
import com.careerbridge.auth.dto.ResetPasswordRequest;
import com.careerbridge.auth.dto.VerifyOtpRequest;
import com.careerbridge.auth.dto.VerifyOtpResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(String refreshToken);

    void logout(String refreshToken);

    void forgotPassword(ForgotPasswordRequest request);

    VerifyOtpResponse verifyOtp(VerifyOtpRequest request);

    void resetPassword(ResetPasswordRequest request);
}
