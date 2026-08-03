package com.careerbridge.payment.service;

import com.careerbridge.payment.dto.external.RazorpayOrderDtos.OrderResponse;
import com.careerbridge.payment.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The only HTTP-level test in this repo. Every other service verifies its clients live, but a
 * money path earns a wire-shape test: this pins that the amount really leaves as an integer in
 * paise, which is the classic factor-of-100 payment bug.
 */
class RazorpayClientTest {

    private static final String BASE_URL = "https://api.razorpay.com/v1";

    private MockRestServiceServer server;

    private RazorpayClient clientWithKey(String keyId) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        return new RazorpayClient(builder.build(), keyId);
    }

    @Test
    void createOrder_SendsAmountAsIntegerPaise() {
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                // 19900, not 199 and not 199.00 -- Razorpay takes the currency subunit.
                .andExpect(jsonPath("$.amount").value(19900))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("CB-21-1"))
                .andRespond(withSuccess("""
                        {"id":"order_abc123","amount":19900,"currency":"INR",
                         "status":"created","receipt":"CB-21-1"}
                        """, MediaType.APPLICATION_JSON));

        OrderResponse response = client.createOrder(19900L, "INR", "CB-21-1");

        assertEquals("order_abc123", response.id());
        server.verify();
    }

    @Test
    void createOrder_MapsResponseFields() {
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders"))
                .andRespond(withSuccess("""
                        {"id":"order_xyz","amount":499900,"currency":"INR","status":"created"}
                        """, MediaType.APPLICATION_JSON));

        OrderResponse response = client.createOrder(499900L, "INR", "CB-21-2");

        assertEquals("order_xyz", response.id());
        assertEquals(499900L, response.amount());
        assertEquals("created", response.status());
        assertFalse(response.isPaid());
    }

    @Test
    void createOrder_IgnoresUnknownResponseFields() {
        // Razorpay returns entity, amount_paid, amount_due, attempts, notes, created_at. Jackson 3
        // has FAIL_ON_UNKNOWN_PROPERTIES off, so the trimmed record must still bind.
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders"))
                .andRespond(withSuccess("""
                        {"id":"order_full","entity":"order","amount":19900,"amount_paid":0,
                         "amount_due":19900,"currency":"INR","receipt":"r","status":"created",
                         "attempts":0,"notes":[],"created_at":1700000000}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("order_full", client.createOrder(19900L, "INR", "r").id());
    }

    @Test
    void createOrder_Razorpay5xx_Throws503() {
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders")).andRespond(withServerError());

        CustomException ex = assertThrows(CustomException.class,
                () -> client.createOrder(19900L, "INR", "CB-21-3"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void createOrder_Razorpay401_Throws503() {
        // Bad keys must not surface as a 500 or leak the upstream body.
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CustomException ex = assertThrows(CustomException.class,
                () -> client.createOrder(19900L, "INR", "CB-21-4"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void createOrder_ResponseWithoutOrderId_Throws503() {
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders"))
                .andRespond(withSuccess("{\"amount\":19900}", MediaType.APPLICATION_JSON));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(CustomException.class,
                        () -> client.createOrder(19900L, "INR", "CB-21-5")).getStatus());
    }

    @Test
    void createOrder_BlankKeyId_Throws503WithoutCallingRazorpay() {
        RazorpayClient client = clientWithKey("");

        CustomException ex = assertThrows(CustomException.class,
                () -> client.createOrder(19900L, "INR", "CB-21-6"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getMessage().contains("not configured"));
        server.verify(); // no request was expected, and none was made
    }

    @Test
    void createOrder_FailureMessage_OmitsKeyMaterial() {
        RazorpayClient client = clientWithKey("rzp_test_supersecret");
        server.expect(requestTo(BASE_URL + "/orders")).andRespond(withServerError());

        CustomException ex = assertThrows(CustomException.class,
                () -> client.createOrder(19900L, "INR", "CB-21-7"));

        assertFalse(ex.getMessage().contains("rzp_test_supersecret"));
    }

    @Test
    void fetchOrder_PaidOrder_ReturnsPaidStatus() {
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders/order_abc123"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"order_abc123","amount":19900,"currency":"INR","status":"paid"}
                        """, MediaType.APPLICATION_JSON));

        OrderResponse response = client.fetchOrder("order_abc123");

        assertTrue(response.isPaid());
        server.verify();
    }

    @Test
    void fetchOrder_UnknownOrder_ReturnsNullNot503() {
        // A 404 from Razorpay means "no such order", which is a client mistake, not an outage.
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders/order_missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertNull(client.fetchOrder("order_missing"));
    }

    @Test
    void fetchOrder_Razorpay5xx_Throws503() {
        RazorpayClient client = clientWithKey("rzp_test_abc");
        server.expect(requestTo(BASE_URL + "/orders/order_abc123")).andRespond(withServerError());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(CustomException.class,
                        () -> client.fetchOrder("order_abc123")).getStatus());
    }

    @Test
    void isConfigured_ReflectsKeyPresence() {
        assertTrue(clientWithKey("rzp_test_abc").isConfigured());
        assertFalse(clientWithKey("").isConfigured());
    }
}
