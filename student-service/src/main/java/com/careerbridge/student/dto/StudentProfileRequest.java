package com.careerbridge.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body of PUT /api/student/profile.
 *
 * Full-replace semantics: a field left null CLEARS the stored value, so the client must send the
 * whole profile on every save. Deliberate -- it is the honest reading of a PUT and it is the only
 * way to blank a field back out.
 *
 * Note what is absent: userId (comes from the X-User-Id header), email (owned by auth-service),
 * resumeUrl and profileCompletionPercentage (both server-derived). None of those are client-writable.
 */
@Data
public class StudentProfileRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Pattern(regexp = "^\\+?[0-9][0-9\\s-]{6,14}$", message = "Enter a valid phone number")
    private String phone;

    @Size(max = 2000, message = "Bio must be at most 2000 characters")
    private String bio;

    @Pattern(regexp = "^[A-Za-z][A-Za-z\\s.'-]{1,49}$", message = "Letters only, no digits")
    private String city;

    @Pattern(regexp = "^[A-Za-z][A-Za-z\\s.'-]{1,49}$", message = "Letters only, no digits")
    private String state;

    @Pattern(regexp = "^[A-Za-z][A-Za-z\\s.'-]{1,49}$", message = "Letters only, no digits")
    private String country;

    @Pattern(regexp = "^https?://.+\\..+$", message = "Enter a full URL starting with https://")
    private String linkedinUrl;

    @Pattern(regexp = "^https?://.+\\..+$", message = "Enter a full URL starting with https://")
    private String githubUrl;

    @Pattern(regexp = "^https?://.+\\..+$", message = "Enter a full URL starting with https://")
    private String portfolioUrl;

    private Boolean isPublic;
}
