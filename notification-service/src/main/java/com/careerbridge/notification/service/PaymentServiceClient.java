package com.careerbridge.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Fetches the invoice PDF bytes from payment-service for a successful subscription.
 *
 * A concrete @Service with no interface, matching EmailService: one implementation, and Mockito
 * mocks classes fine.
 *
 * Calls payment-service directly on the compose network, NOT through api-gateway -- the gateway
 * exists to validate JWTs from outside the system, and this call originates inside it carrying no
 * token to validate. Forwards X-User-Id/X-User-Role from the event's own userId/userRole, never an
 * elevated role: this is a financial document, and PaymentServiceImpl's ownership check treats this
 * call exactly like the paying user's own browser request.
 */
@Service
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String INVOICE_DOWNLOAD_PATH = "/api/payment/invoices/{paymentId}/download";

    private final RestClient paymentRestClient;

    public PaymentServiceClient(RestClient paymentRestClient) {
        this.paymentRestClient = paymentRestClient;
    }

    /**
     * Never throws. A payment-service outage must not stop the confirmation email: the user was
     * genuinely charged, and telling them so without the attachment is strictly better than telling
     * them nothing while an event sits queued and their inbox stays empty.
     *
     * Returns null on any failure -- outage, timeout, non-2xx (e.g. the subscription row is
     * genuinely missing), or an empty body. Same broad-catch shape as prs-service's
     * StudentServiceClient.fetchProfileScore, for the same reason: RestClient.retrieve() throws on
     * 4xx/5xx, the request factory throws on connect/read timeouts, and all of them mean the same
     * thing to this caller.
     */
    public byte[] fetchInvoicePdf(Long paymentId, Long userId, String userRole) {
        if (paymentId == null || userId == null) {
            log.warn("Cannot fetch invoice for a null paymentId or userId");
            return null;
        }

        try {
            byte[] pdf = paymentRestClient.get()
                    .uri(INVOICE_DOWNLOAD_PATH, paymentId)
                    .header(USER_ID_HEADER, userId.toString())
                    .header(USER_ROLE_HEADER, userRole)
                    .retrieve()
                    .body(byte[].class);

            if (pdf == null || pdf.length == 0) {
                log.warn("payment-service returned no invoice bytes for paymentId={}", paymentId);
                return null;
            }

            return pdf;
        } catch (Exception ex) {
            log.warn("Failed to fetch invoice PDF for paymentId={}: {}. Sending email without attachment.",
                    paymentId, ex.getMessage());
            return null;
        }
    }
}
