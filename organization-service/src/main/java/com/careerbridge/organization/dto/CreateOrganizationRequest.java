package com.careerbridge.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrganizationRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Type is required")
    private String type;

    @Email(message = "Contact email must be valid")
    private String contactEmail;

    private String contactPhone;

    private String address;

    private String city;

    private String state;
}
