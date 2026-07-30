package com.careerbridge.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponse {

    private Long id;

    private String name;

    private String type;

    private String contactEmail;

    private String contactPhone;

    private String address;

    private String city;

    private String state;

    private Boolean isActive;

    private LocalDateTime createdAt;

    /**
     * Populated inside the service's transaction. Organization.departments is LAZY, so building
     * this list after the transaction closes throws LazyInitializationException at serialisation
     * time rather than at the call site, which is a confusing place to debug it.
     */
    private List<DepartmentResponse> departments;
}
