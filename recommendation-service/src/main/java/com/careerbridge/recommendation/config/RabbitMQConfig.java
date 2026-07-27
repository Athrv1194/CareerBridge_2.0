package com.careerbridge.recommendation.config;

import com.careerbridge.recommendation.constants.RecommendationConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This service both consumes (assessment.completed) and publishes (recommendation.generated), so it
 * needs the consumer half -- queue and binding -- and the producer half -- an explicit
 * RabbitTemplate -- unlike student-service, which only consumes.
 */
@Configuration
public class RabbitMQConfig {

    /**
     * Durable so queued assessments survive a broker restart; otherwise the student completes an
     * assessment and simply never gets a recommendation.
     */
    @Bean
    public Queue recommendationQueue() {
        return new Queue(RecommendationConstants.QUEUE_NAME, true);
    }

    /**
     * durable=true, autoDelete=false must match auth-service, student-service and
     * assessment-service exactly. A mismatch is answered with 406 PRECONDITION_FAILED, and because
     * declaration happens asynchronously on the connection callback, the consumer would simply
     * never start rather than failing loudly at boot.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(RecommendationConstants.EXCHANGE_NAME, true, false);
    }

    @Bean
    public Binding assessmentCompletedBinding(Queue recommendationQueue,
                                              TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(recommendationQueue)
                .to(careerBridgeExchange)
                .with(RecommendationConstants.ROUTING_KEY_ASSESSMENT_COMPLETED);
    }

    /** JacksonJson (Jackson 3), not the deprecated Jackson2 variant -- Boot 4 ships tools.jackson. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Declared explicitly rather than left to Boot's auto-configured template. Boot's
     * RabbitTemplateConfigurer only applies a MessageConverter bean via ifUnique(...), so the day a
     * second converter bean appears on the context the outgoing RecommendationGeneratedEvent would
     * silently fall back to Java serialization and notification-service could not read it. Setting
     * it here makes the JSON contract independent of how many converter beans exist.
     *
     * Boot still injects this same bean into the listener container factory for the consumer side,
     * so one converter serves both directions.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
