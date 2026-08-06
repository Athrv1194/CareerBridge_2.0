package com.careerbridge.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SubmitOrgRequest {

    @NotBlank(message = "Institution name is required")
    private String institutionName;

    @NotBlank(message = "Institution code is required")
    private String institutionCode;

    @NotBlank(message = "Contact person name is required")
    private String contactPersonName;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email must be valid")
    private String contactEmail;

    @NotBlank(message = "Contact phone is required")
    @Pattern(regexp = "^[+]?[0-9 ()-]{7,20}$", message = "Contact phone must be a valid phone number")
    private String contactPhone;

    private String websiteDomain;

    private String address;

    private String city;

    private String state;

    @NotBlank(message = "Institution type is required")
    private String organizationType;
}
