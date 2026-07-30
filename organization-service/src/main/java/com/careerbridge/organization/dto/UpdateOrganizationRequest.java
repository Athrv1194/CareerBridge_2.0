package com.careerbridge.organization.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * Every field optional: this is a partial update, and a null means "leave as is" rather than
 * "clear". A caller who wants to blank a field sends an empty string.
 */
@Data
public class UpdateOrganizationRequest {

    private String name;

    private String type;

    @Email(message = "Contact email must be valid")
    private String contactEmail;

    private String contactPhone;

    private String address;

    private String city;

    private String state;
}
