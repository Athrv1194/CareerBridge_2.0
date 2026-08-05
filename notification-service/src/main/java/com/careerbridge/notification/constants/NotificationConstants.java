package com.careerbridge.notification.constants;

public class NotificationConstants {

    /** Must match the exchange auth/student/assessment/recommendation services already declare. */
    public static final String EXCHANGE_NAME = "careerbridge.exchange";

    /**
     * This service owns two queues, one per event type -- not one queue with two listeners.
     *
     * Two @RabbitListener methods on a single queue create two independent containers consuming it,
     * and RabbitMQ round-robins deliveries between them. Roughly half of every event would be handed
     * to the wrong listener, where TypePrecedence.INFERRED would bind the JSON into the wrong class.
     * Jackson 3 disables FAIL_ON_UNKNOWN_PROPERTIES, so that does not throw -- it silently yields an
     * all-null object the null guard discards with a WARN. Half the notifications would vanish
     * without an error anywhere. Separate queues make each listener's type unambiguous.
     */
    public static final String QUEUE_NAME = "careerbridge.notification.queue";

    /**
     * Deliberately NOT "careerbridge.student.queue" -- that queue belongs to student-service.
     * Declaring it here would make this service a second consumer on it and steal half of
     * student-service's profile-creation events.
     */
    public static final String STUDENT_QUEUE_NAME = "careerbridge.notification.student.queue";

    /** Literal must equal RecommendationConstants.ROUTING_KEY_RECOMMENDATION_GENERATED. */
    public static final String ROUTING_KEY_RECOMMENDATION_GENERATED = "recommendation.generated";

    /** Literal must equal auth-service's RabbitMQConfig.STUDENT_REGISTERED_ROUTING_KEY. */
    public static final String ROUTING_KEY_STUDENT_REGISTERED = "student.registered";

    /** Own queue, per the same one-queue-per-event-type rule as the two above. */
    public static final String PASSWORD_RESET_QUEUE_NAME = "careerbridge.notification.password.reset.queue";
    public static final String PASSWORD_CHANGED_QUEUE_NAME = "careerbridge.notification.password.changed.queue";

    /** Literals must equal auth-service's RabbitMQConfig.PASSWORD_RESET_REQUESTED_ROUTING_KEY / PASSWORD_CHANGED_ROUTING_KEY. */
    public static final String ROUTING_KEY_PASSWORD_RESET_REQUESTED = "password.reset.requested";
    public static final String ROUTING_KEY_PASSWORD_CHANGED = "password.changed";

    public static final String EMAIL_SUBJECT = "Your Career Recommendation is Ready!";
    public static final String PASSWORD_RESET_EMAIL_SUBJECT = "Your CareerBridge password reset code";
    public static final String PASSWORD_CHANGED_EMAIL_SUBJECT = "Your CareerBridge password was changed";

    /** NotificationRecord.notificationType -- the delivery channel being audited. */
    public static final String TYPE_EMAIL = "EMAIL";

    /** NotificationDocument.notificationType -- what the in-app notification is about. */
    public static final String TYPE_RECOMMENDATION = "RECOMMENDATION";

    /** NotificationRecord.status values. Strings rather than an enum, per the service contract. */
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * There is deliberately no FROM_EMAIL constant. The sender address is read from
     * spring.mail.username instead (injected into EmailService), because Gmail rejects any From
     * that is not the authenticated user or a verified alias -- two sources of truth for that
     * address is how a working setup starts returning 553 weeks later.
     */
    private NotificationConstants() {
    }
}
