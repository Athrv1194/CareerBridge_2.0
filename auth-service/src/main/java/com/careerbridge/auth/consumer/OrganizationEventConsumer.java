package com.careerbridge.auth.consumer;

import com.careerbridge.auth.config.RabbitMQConfig;
import com.careerbridge.auth.event.OrganizationRequestApprovedEvent;
import com.careerbridge.auth.service.OrgAdminProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Mirrors SubscriptionEventConsumer exactly.
 *
 * The parameter type must stay the concrete OrganizationRequestApprovedEvent. Spring AMQP's default
 * TypePrecedence.INFERRED resolves the payload from this method signature, which is the only reason
 * the differing package FQN between organization-service and this service works with zero
 * type-mapper configuration. Widening it to Object or Message falls back to the sender's __TypeId__
 * header and dies with ClassNotFoundException.
 *
 * Fail-soft and deliberately not @Transactional: rethrowing would requeue the message and spin the
 * listener forever on a payload this service can never process.
 *
 * Do NOT add a second @RabbitListener to this queue. Two containers on one queue make RabbitMQ
 * round-robin between them, so roughly half of each event type would be bound into the wrong class.
 */
@Component
public class OrganizationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrganizationEventConsumer.class);

    private final OrgAdminProvisioningService orgAdminProvisioningService;

    public OrganizationEventConsumer(OrgAdminProvisioningService orgAdminProvisioningService) {
        this.orgAdminProvisioningService = orgAdminProvisioningService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORGANIZATION_QUEUE)
    public void onOrganizationRequestApproved(OrganizationRequestApprovedEvent event) {
        try {
            if (event == null || event.getOrganizationId() == null || event.getAdminEmail() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        RabbitMQConfig.ORGANIZATION_REQUEST_APPROVED_ROUTING_KEY, event);
                return;
            }
            log.info("Received organization.request.approved: organizationId={}, adminEmail={}",
                    event.getOrganizationId(), event.getAdminEmail());
            orgAdminProvisioningService.provision(event);
        } catch (Exception ex) {
            log.error("Failed to process organization.request.approved for organizationId={}: {}",
                    event == null ? null : event.getOrganizationId(), ex.getMessage());
        }
    }
}
