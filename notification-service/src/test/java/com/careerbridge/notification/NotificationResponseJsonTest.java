package com.careerbridge.notification;

import com.careerbridge.notification.dto.NotificationResponse;
import com.careerbridge.notification.dto.UnreadCountResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the JSON field names the frontend reads.
 *
 * The trap this guards: if isRead is ever changed from Boolean to primitive boolean, Lombok
 * generates isRead() instead of getIsRead(), the JavaBeans property name collapses to "read", and
 * Jackson silently emits {"read": false}. Nothing fails at build time -- the frontend just stops
 * seeing the field. Same reasoning for unreadCount being a long rather than an int.
 *
 * Jackson 3 (tools.jackson), which is the only Jackson on this service's compile classpath.
 */
class NotificationResponseJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("json: NotificationResponse serialises isRead, never read")
    void notificationResponse_SerialisesIsReadNotRead() {
        String json = MAPPER.writeValueAsString(NotificationResponse.builder()
                .id("6650f1c2a1b2c3d4e5f60718")
                .userId(42L)
                .recommendationId(7L)
                .title("Your Career Recommendation is Ready!")
                .message("Based on your System Design assessment...")
                .isRead(false)
                .notificationType("RECOMMENDATION")
                .build());

        assertTrue(json.contains("\"isRead\":false"), json);
        assertFalse(json.contains("\"read\":"), "a primitive boolean would emit \"read\" instead: " + json);

        // The rest of the contract, so a field rename shows up here too.
        assertTrue(json.contains("\"id\":\"6650f1c2a1b2c3d4e5f60718\""), json);
        assertTrue(json.contains("\"recommendationId\":7"), json);
        assertTrue(json.contains("\"notificationType\":\"RECOMMENDATION\""), json);
    }

    @Test
    @DisplayName("json: UnreadCountResponse serialises unreadCount as a number")
    void unreadCountResponse_SerialisesUnreadCount() {
        String json = MAPPER.writeValueAsString(UnreadCountResponse.builder()
                .userId(42L)
                .unreadCount(7L)
                .build());

        assertTrue(json.contains("\"unreadCount\":7"), json);
        assertTrue(json.contains("\"userId\":42"), json);
    }
}
