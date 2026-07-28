package com.careerbridge.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One in-app notification as the frontend consumes it. Mirrors NotificationDocument. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    /** Mongo ObjectId hex, so a String -- this is also the {notificationId} path variable type. */
    private String id;

    private Long userId;

    private Long recommendationId;

    private String title;

    private String message;

    /**
     * Boolean, not primitive boolean: with a primitive, Lombok generates isRead(), the JavaBeans
     * property collapses to "read", and Jackson would serialise {"read": ...} -- silently changing
     * the JSON contract the frontend reads.
     */
    private Boolean isRead;

    private String notificationType;

    private LocalDateTime createdAt;

    /** Null until the student opens it. */
    private LocalDateTime readAt;
}
