package com.careerbridge.auth.service;

import com.careerbridge.auth.dto.AuthResponse;
import com.careerbridge.auth.dto.LoginRequest;
import com.careerbridge.auth.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(String refreshToken);

    void logout(String refreshToken);
}
