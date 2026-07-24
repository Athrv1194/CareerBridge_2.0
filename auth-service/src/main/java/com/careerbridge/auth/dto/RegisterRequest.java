package com.careerbridge.auth.dto;

import com.careerbridge.auth.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    // max 72: BCrypt only hashes the first 72 bytes and Spring Security throws
    // on anything longer, so cap it here for a clean 400 instead of a 500.
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;

    private Role role = Role.STUDENT;

    private Long organizationId;
}
