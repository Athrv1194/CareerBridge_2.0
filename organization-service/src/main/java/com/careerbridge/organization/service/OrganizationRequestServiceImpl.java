package com.careerbridge.organization.service;

import com.careerbridge.organization.config.RabbitMQConfig;
import com.careerbridge.organization.dto.OrgRequestResponse;
import com.careerbridge.organization.dto.RejectOrgRequest;
import com.careerbridge.organization.dto.SubmitOrgRequest;
import com.careerbridge.organization.event.OrganizationCreatedEvent;
import com.careerbridge.organization.event.OrganizationRequestApprovedEvent;
import com.careerbridge.organization.exception.CustomException;
import com.careerbridge.organization.model.Organization;
import com.careerbridge.organization.model.OrganizationRequest;
import com.careerbridge.organization.model.RequestStatus;
import com.careerbridge.organization.repository.OrganizationRepository;
import com.careerbridge.organization.repository.OrganizationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrganizationRequestServiceImpl implements OrganizationRequestService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationRequestServiceImpl.class);

    /** Same string, same reasoning as OrganizationServiceImpl -- X-User-Role is what arrives here. */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final OrganizationRequestRepository organizationRequestRepository;
    private final OrganizationRepository organizationRepository;
    private final RabbitTemplate rabbitTemplate;

    public OrganizationRequestServiceImpl(OrganizationRequestRepository organizationRequestRepository,
                                          OrganizationRepository organizationRepository,
                                          RabbitTemplate rabbitTemplate) {
        this.organizationRequestRepository = organizationRequestRepository;
        this.organizationRepository = organizationRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public OrgRequestResponse submit(SubmitOrgRequest request) {
        if (organizationRequestRepository.existsByInstitutionCodeIgnoreCase(request.getInstitutionCode())) {
            throw new CustomException("An application with this institution code already exists",
                    HttpStatus.CONFLICT);
        }
        if (organizationRequestRepository.existsByInstitutionNameIgnoreCaseAndStatus(
                request.getInstitutionName(), RequestStatus.PENDING)) {
            throw new CustomException("An application for this institution is already pending review",
                    HttpStatus.CONFLICT);
        }

        OrganizationRequest saved = organizationRequestRepository.save(OrganizationRequest.builder()
                .institutionName(request.getInstitutionName())
                .institutionCode(request.getInstitutionCode())
                .contactPersonName(request.getContactPersonName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .websiteDomain(request.getWebsiteDomain())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .organizationType(request.getOrganizationType())
                .build());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrgRequestResponse> list(RequestStatus status, String callerRole) {
        requireSuperAdmin(callerRole);

        List<OrganizationRequest> requests = status == null
                ? organizationRequestRepository.findAllByOrderByRequestedAtDesc()
                : organizationRequestRepository.findByStatusOrderByRequestedAtDesc(status);

        return requests.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrgRequestResponse getById(Long id, String callerRole) {
        requireSuperAdmin(callerRole);

        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public OrgRequestResponse approve(Long id, String callerRole, Long callerUserId) {
        requireSuperAdmin(callerRole);

        OrganizationRequest orgRequest = findOrThrow(id);

        // Idempotent approvals requirement: a second approve on an already-decided request must not
        // create a second Organization, and answers 409 rather than silently repeating success.
        if (orgRequest.getStatus() != RequestStatus.PENDING) {
            throw new CustomException("This application has already been reviewed", HttpStatus.CONFLICT);
        }

        if (organizationRepository.existsByNameIgnoreCase(orgRequest.getInstitutionName())) {
            throw new CustomException("An organization with this name already exists", HttpStatus.CONFLICT);
        }

        Organization organization = organizationRepository.save(Organization.builder()
                .name(orgRequest.getInstitutionName())
                .type(orgRequest.getOrganizationType())
                .contactEmail(orgRequest.getContactEmail())
                .contactPhone(orgRequest.getContactPhone())
                .address(orgRequest.getAddress())
                .city(orgRequest.getCity())
                .state(orgRequest.getState())
                .build());

        orgRequest.setStatus(RequestStatus.APPROVED);
        orgRequest.setCreatedOrganizationId(organization.getId());
        orgRequest.setReviewedAt(LocalDateTime.now());
        orgRequest.setReviewedByUserId(callerUserId);
        organizationRequestRepository.save(orgRequest);

        publishOrganizationCreated(organization);
        publishRequestApproved(orgRequest, organization);

        return toResponse(orgRequest);
    }

    @Override
    @Transactional
    public OrgRequestResponse reject(Long id, RejectOrgRequest request, String callerRole, Long callerUserId) {
        requireSuperAdmin(callerRole);

        OrganizationRequest orgRequest = findOrThrow(id);

        if (orgRequest.getStatus() != RequestStatus.PENDING) {
            throw new CustomException("This application has already been reviewed", HttpStatus.CONFLICT);
        }

        orgRequest.setStatus(RequestStatus.REJECTED);
        orgRequest.setRejectionReason(request.getReason());
        orgRequest.setReviewedAt(LocalDateTime.now());
        orgRequest.setReviewedByUserId(callerUserId);

        return toResponse(organizationRequestRepository.save(orgRequest));
    }

    // ---------------------------------------------------------------------------------------------
    // Authorization and lookups
    // ---------------------------------------------------------------------------------------------

    private void requireSuperAdmin(String callerRole) {
        if (!ROLE_SUPER_ADMIN.equals(callerRole)) {
            throw new CustomException("Only SUPER_ADMIN may perform this operation", HttpStatus.FORBIDDEN);
        }
    }

    private OrganizationRequest findOrThrow(Long id) {
        return organizationRequestRepository.findById(id)
                .orElseThrow(() -> new CustomException("Application not found", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------------------------------------
    // Events and mapping
    // ---------------------------------------------------------------------------------------------

    /** Fail-soft, same reasoning as OrganizationServiceImpl.publishCreated: the row is already committed. */
    private void publishOrganizationCreated(Organization organization) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ORGANIZATION_CREATED_ROUTING_KEY,
                    OrganizationCreatedEvent.builder()
                            .organizationId(organization.getId())
                            .name(organization.getName())
                            .type(organization.getType())
                            .contactEmail(organization.getContactEmail())
                            .createdAt(organization.getCreatedAt())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for organizationId={}",
                    RabbitMQConfig.ORGANIZATION_CREATED_ROUTING_KEY, organization.getId(), ex);
        }
    }

    private void publishRequestApproved(OrganizationRequest orgRequest, Organization organization) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ORGANIZATION_REQUEST_APPROVED_ROUTING_KEY,
                    OrganizationRequestApprovedEvent.builder()
                            .requestId(orgRequest.getId())
                            .organizationId(organization.getId())
                            .organizationName(organization.getName())
                            .organizationCode(orgRequest.getInstitutionCode())
                            .adminName(orgRequest.getContactPersonName())
                            .adminEmail(orgRequest.getContactEmail())
                            .adminPhone(orgRequest.getContactPhone())
                            .approvedAt(orgRequest.getReviewedAt())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for requestId={}",
                    RabbitMQConfig.ORGANIZATION_REQUEST_APPROVED_ROUTING_KEY, orgRequest.getId(), ex);
        }
    }

    private OrgRequestResponse toResponse(OrganizationRequest orgRequest) {
        return OrgRequestResponse.builder()
                .id(orgRequest.getId())
                .institutionName(orgRequest.getInstitutionName())
                .institutionCode(orgRequest.getInstitutionCode())
                .contactPersonName(orgRequest.getContactPersonName())
                .contactEmail(orgRequest.getContactEmail())
                .contactPhone(orgRequest.getContactPhone())
                .websiteDomain(orgRequest.getWebsiteDomain())
                .address(orgRequest.getAddress())
                .city(orgRequest.getCity())
                .state(orgRequest.getState())
                .organizationType(orgRequest.getOrganizationType())
                .status(orgRequest.getStatus())
                .rejectionReason(orgRequest.getRejectionReason())
                .createdOrganizationId(orgRequest.getCreatedOrganizationId())
                .requestedAt(orgRequest.getRequestedAt())
                .reviewedAt(orgRequest.getReviewedAt())
                .reviewedByUserId(orgRequest.getReviewedByUserId())
                .build();
    }
}
