package com.careerbridge.organization.service;

import com.careerbridge.organization.dto.OrgRequestResponse;
import com.careerbridge.organization.dto.RejectOrgRequest;
import com.careerbridge.organization.dto.SubmitOrgRequest;
import com.careerbridge.organization.model.RequestStatus;

import java.util.List;

public interface OrganizationRequestService {

    OrgRequestResponse submit(SubmitOrgRequest request);

    List<OrgRequestResponse> list(RequestStatus status, String callerRole);

    OrgRequestResponse getById(Long id, String callerRole);

    OrgRequestResponse approve(Long id, String callerRole, Long callerUserId);

    OrgRequestResponse reject(Long id, RejectOrgRequest request, String callerRole, Long callerUserId);
}
