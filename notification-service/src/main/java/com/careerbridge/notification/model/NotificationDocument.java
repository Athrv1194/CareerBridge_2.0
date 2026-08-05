package com.careerbridge.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * The student-facing in-app notification feed, in MongoDB.
 *
 * Deliberately no @Indexed anywhere: MongoMappingContext.autoIndexCreation is a primitive boolean
 * defaulting to false, so the annotation would be a silent no-op, and switching it on
 * (spring.data.mongodb.auto-index-creation: true) would make a reachable Mongo mandatory at
 * startup for every developer and for CI. If findByUserIdOrderByCreatedAtDesc ever gets slow,
 * create the index once by hand in mongosh and note it in CLAUDE.md.
 */
@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDocument {

    /** Mongo ObjectId hex -- a String, which is why the REST path variable is a String too. */
    @Id
    private String id;

    private Long userId;

    private Long recommendationId;

    /**
     * Null for every notification type except SUBSCRIPTION. Mongo is schemaless, so adding this
     * costs no migration and no risk to existing documents -- unlike NotificationRecord's Postgres
     * schema, whose recommendationId is NOT NULL and half a unique constraint, which is exactly why
     * subscription-invoice notifications get no Postgres audit row at all.
     */
    private Long paymentId;

    private String title;

    private String message;

    /**
     * Boolean, never primitive boolean. With a primitive, Lombok generates isRead(), the JavaBeans
     * property name collapses to "read", Jackson would emit {"read": false} -- silently breaking
     * the frontend contract -- and Spring Data could fail to resolve findByUserIdAndIsReadFalse.
     *
     * @Builder.Default is load-bearing: without it Lombok's builder ignores this initializer and
     * writes null, and a Mongo query for {isRead: false} does not match null -- so every brand new
     * notification would be invisible to the unread count and the unread filter.
     */
    @Builder.Default
    private Boolean isRead = false;

    /** NotificationConstants.TYPE_RECOMMENDATION -- what the notification is about. */
    private String notificationType;

    /**
     * Set explicitly by the service, NOT by @CreationTimestamp: that annotation is
     * org.hibernate.annotations.CreationTimestamp and only fires on Hibernate's ORM insert path.
     * On a Mongo document it does nothing at all, silently leaving this null and breaking the
     * findByUserIdOrderByCreatedAtDesc ordering.
     */
    private LocalDateTime createdAt;

    /** Null until the student reads it; set alongside isRead=true by markAsRead. */
    private LocalDateTime readAt;
}
