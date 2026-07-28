package com.careerbridge.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Delivery audit trail, in PostgreSQL. One row per (user, recommendation) attempt, recording
 * whether the email actually went out. The student-facing feed lives in Mongo
 * (NotificationDocument); this table is the system of record nobody but an operator reads.
 */
@Entity
@Table(
        name = "notification_records",
        // Column names, NOT field names -- Boot's CamelCaseToUnderscoresNamingStrategy maps
        // userId -> user_id, and writing the field names here fails at startup with
        // "column not found". This constraint, not the existsBy... call, is the real idempotency
        // guarantee when two brokers redeliver the same event concurrently.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_records_user_recommendation",
                columnNames = {"user_id", "recommendation_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long recommendationId;

    /** Delivery channel being audited -- NotificationConstants.TYPE_EMAIL today. */
    @Column(nullable = false)
    private String notificationType;

    /** NotificationConstants.STATUS_SENT or STATUS_FAILED. */
    @Column(nullable = false)
    private String status;

    /** Null when no UserContact row existed, which is itself a recorded FAILED reason. */
    private String recipientEmail;

    private String subject;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime sentAt;

    /**
     * Only populated on FAILED. TEXT rather than @Lob: Hibernate's PostgreSQLDialect maps a bare
     * @Lob String to the oid large-object type, which rejects a plain string on the ORM insert
     * path -- the SEV-2 logged in ai_incident_log.md. Also wider than the 255 default, since an
     * SMTP failure message is easily longer.
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
