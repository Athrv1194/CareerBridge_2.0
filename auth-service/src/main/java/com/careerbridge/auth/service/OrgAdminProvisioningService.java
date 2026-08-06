package com.careerbridge.auth.service;

import com.careerbridge.auth.event.OrganizationRequestApprovedEvent;

public interface OrgAdminProvisioningService {

    void provision(OrganizationRequestApprovedEvent event);
}
