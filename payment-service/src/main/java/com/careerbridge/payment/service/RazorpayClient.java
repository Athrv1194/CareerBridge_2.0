package com.careerbridge.payment.service;

import com.careerbridge.payment.dto.external.RazorpayOrderDtos.CreateOrderRequest;
import com.careerbridge.payment.dto.external.RazorpayOrderDtos.OrderResponse;
import com.careerbridge.payment.exception.CustomException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Razorpay's Orders API. Two calls, both server-to-server with HTTP Basic auth.
 *
 * Failure contract: throws CustomException 503, never returns a sentinel. Unlike the fail-soft
 * clients elsewhere in this project (which degrade a coaching prompt or a candidate score), a
 * Razorpay outage here means the student genuinely cannot pay, and the honest answer is "try
 * again shortly" rather than a half-working page.
 *
 * Nothing in this class ever logs the request body, the response body, or the key secret.
 */
@Service
public class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);

    private static final String ORDERS_PATH = "/orders";

    private final RestClient razorpayRestClient;
    private final String keyId;

    public RazorpayClient(@Qualifier("razorpayRestClient") RestClient razorpayRestClient,
                          @Value("${razorpay.key.id:}") String keyId) {
        this.razorpayRestClient = razorpayRestClient;
        this.keyId = keyId;
    }

    /** The public key id, returned to the browser so Checkout.js can open the modal. */
    public String getKeyId() {
        return keyId;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(keyId);
    }

    /**
     * amountPaise is already in the currency subunit -- callers use Money.toPaise, never a raw
     * multiplication at the call site.
     */
    public OrderResponse createOrder(long amountPaise, String currency, String receipt) {
        requireConfigured();
        try {
            OrderResponse response = razorpayRestClient.post()
                    .uri(ORDERS_PATH)
                    .body(new CreateOrderRequest(amountPaise, currency, receipt))
                    .retrieve()
                    .body(OrderResponse.class);

            if (response == null || !StringUtils.hasText(response.id())) {
                throw new IllegalStateException("Razorpay returned no order id");
            }
            log.info("Created Razorpay order {} for {} {}", response.id(), currency, amountPaise);
            return response;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // getMessage() only -- never the stack trace or the body, either of which could carry
            // the Authorization header on some client implementations.
            log.error("Razorpay createOrder failed: {}", e.getMessage());
            throw unavailable();
        }
    }

    /**
     * Backs the reconcile path: if the browser closed before /verify, Razorpay still knows whether
     * the order was paid. Returns null when Razorpay does not know the order (404) -- that is a
     * real state, a client typo, not an outage. Same three-state shape as ai-coach-service's
     * RoadmapServiceClient.fetchMyRoadmap.
     */
    public OrderResponse fetchOrder(String razorpayOrderId) {
        requireConfigured();
        try {
            return razorpayRestClient.get()
                    .uri(ORDERS_PATH + "/{id}", razorpayOrderId)
                    .retrieve()
                    .body(OrderResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.error("Razorpay fetchOrder failed for {}: {}", razorpayOrderId, e.getMessage());
            throw unavailable();
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            log.warn("RAZORPAY_KEY_ID is not set; refusing to call Razorpay");
            throw new CustomException(
                    "Payments are not configured yet - please try again later",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private CustomException unavailable() {
        return new CustomException(
                "Payment provider is temporarily unavailable - please try again later",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
