package com.careerbridge.notification.config;

import com.careerbridge.notification.constants.NotificationConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two queues, one per event type -- not one queue with two listeners.
 *
 * Two @RabbitListener methods on a single queue produce two independent listener containers
 * consuming it, and RabbitMQ round-robins deliveries between them. Roughly half of each event type
 * would reach the wrong listener, where TypePrecedence.INFERRED binds the JSON into the wrong
 * class. Jackson 3 disables FAIL_ON_UNKNOWN_PROPERTIES, so that does not throw -- it yields an
 * object with every field null, which the consumer's null guard discards with a WARN. Half the
 * notifications would disappear with no error anywhere. Separate queues make each listener's
 * payload type unambiguous.
 *
 * This service only consumes, so there is deliberately no RabbitTemplate bean.
 */
@Configuration
public class RabbitMQConfig {

    /** Durable so a queued recommendation survives a broker restart. */
    @Bean
    public Queue notificationQueue() {
        return new Queue(NotificationConstants.QUEUE_NAME, true);
    }

    /**
     * Durable, and deliberately NOT student-service's careerbridge.student.queue -- declaring that
     * name here would make this service a second consumer on it and steal half of student-service's
     * profile-creation events.
     */
    @Bean
    public Queue notificationStudentQueue() {
        return new Queue(NotificationConstants.STUDENT_QUEUE_NAME, true);
    }

    @Bean
    public Queue notificationPasswordResetQueue() {
        return new Queue(NotificationConstants.PASSWORD_RESET_QUEUE_NAME, true);
    }

    @Bean
    public Queue notificationPasswordChangedQueue() {
        return new Queue(NotificationConstants.PASSWORD_CHANGED_QUEUE_NAME, true);
    }

    /**
     * Durable, and its own queue -- a new @RabbitListener on either queue above would create a
     * second container round-robining against the first, same hazard as the other two.
     */
    @Bean
    public Queue notificationSubscriptionQueue() {
        return new Queue(NotificationConstants.SUBSCRIPTION_QUEUE_NAME, true);
    }

    /**
     * durable=true, autoDelete=false must match auth/student/assessment/recommendation exactly. A
     * mismatch is answered with 406 PRECONDITION_FAILED, and because declaration happens
     * asynchronously on the connection callback, the consumers would simply never start rather
     * than failing loudly at boot.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(NotificationConstants.EXCHANGE_NAME, true, false);
    }

    @Bean
    public Binding recommendationGeneratedBinding(Queue notificationQueue,
                                                  TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(careerBridgeExchange)
                .with(NotificationConstants.ROUTING_KEY_RECOMMENDATION_GENERATED);
    }

    @Bean
    public Binding studentRegisteredBinding(Queue notificationStudentQueue,
                                            TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(notificationStudentQueue)
                .to(careerBridgeExchange)
                .with(NotificationConstants.ROUTING_KEY_STUDENT_REGISTERED);
    }

    @Bean
    public Binding passwordResetRequestedBinding(Queue notificationPasswordResetQueue,
                                                 TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(notificationPasswordResetQueue)
                .to(careerBridgeExchange)
                .with(NotificationConstants.ROUTING_KEY_PASSWORD_RESET_REQUESTED);
    }

    @Bean
    public Binding passwordChangedBinding(Queue notificationPasswordChangedQueue,
                                          TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(notificationPasswordChangedQueue)
                .to(careerBridgeExchange)
                .with(NotificationConstants.ROUTING_KEY_PASSWORD_CHANGED);
    }

    @Bean
    public Binding subscriptionActivatedBinding(Queue notificationSubscriptionQueue,
                                                TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(notificationSubscriptionQueue)
                .to(careerBridgeExchange)
                .with(NotificationConstants.ROUTING_KEY_SUBSCRIPTION_ACTIVATED);
    }

    /**
     * JacksonJson (Jackson 3), not the deprecated Jackson2 variant -- Boot 4 ships tools.jackson.
     * Boot injects this single MessageConverter bean into the listener container factory, so no
     * custom SimpleRabbitListenerContainerFactory is needed.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
