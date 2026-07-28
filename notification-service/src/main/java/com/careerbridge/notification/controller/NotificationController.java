package com.careerbridge.notification.controller;

import com.careerbridge.notification.dto.NotificationResponse;
import com.careerbridge.notification.dto.UnreadCountResponse;
import com.careerbridge.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * userId arrives in X-User-Id, forwarded by the API Gateway after it validates the JWT -- this
 * service never parses tokens itself.
 *
 * That makes the header a trusted input, so port 8085 must not be publicly reachable: anyone who
 * can hit it directly can set the header to any value and read or mutate that student's
 * notifications. This is a network control (security group / bind address), not something the code
 * can enforce.
 *
 * Path is /api/notification, SINGULAR. api-gateway routes Path=/api/notification/** and gateway
 * predicates match whole path segments, so a plural mapping here would 404 every request that came
 * through the gateway. All four sibling services are singular too.
 *
 * Notifications are created by the RabbitMQ consumers reacting to events, never by an HTTP call --
 * hence no POST, and no request body anywhere in this service.
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** The student's feed, newest first. Empty list, not 404, when they have none. */
    @GetMapping("/my")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
            @RequestHeader(USER_ID_HEADER) Long userId) {
        return ResponseEntity.ok(notificationService.getMyNotifications(userId));
    }

    /**
     * Unread badge count.
     *
     * Declared before /{notificationId}/read only for readability -- these two cannot collide
     * anyway, since this path has one segment and that one has two.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader(USER_ID_HEADER) Long userId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    /**
     * 404 when the notification does not exist or belongs to another student.
     *
     * notificationId is a String, not a Long: Mongo's @Id is a 24-character ObjectId hex, so a
     * Long path variable would reject every real id with a 400.
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable String notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(userId, notificationId));
    }
}
