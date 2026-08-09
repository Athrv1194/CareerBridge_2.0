package com.careerbridge.organization.dto;

import com.careerbridge.organization.model.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgRequestResponse {

    private Long id;

    private String institutionName;

    private String institutionCode;

    private String contactPersonName;

    private String contactEmail;

    private String contactPhone;

    private String websiteDomain;

    private String address;

    private String city;

    private String state;

    private String organizationType;

    private RequestStatus status;

    private String rejectionReason;

    private Long createdOrganizationId;

    private LocalDateTime requestedAt;

    private LocalDateTime reviewedAt;

    private Long reviewedByUserId;
}
