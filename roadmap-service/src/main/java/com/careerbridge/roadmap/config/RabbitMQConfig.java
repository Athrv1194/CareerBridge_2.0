package com.careerbridge.roadmap.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This service now only publishes (roadmap.updated) -- roadmaps are built on-demand via
 * POST /api/roadmap, not by consuming recommendation.generated, so there is no listener and no
 * consumer half. Only the exchange and an explicit RabbitTemplate remain.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";
    public static final String ROADMAP_UPDATED_ROUTING_KEY = "roadmap.updated";

    /**
     * durable=true, autoDelete=false must match every other service's declaration of this same
     * exchange exactly. A mismatch is answered with 406 PRECONDITION_FAILED.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /** JacksonJson (Jackson 3), not the deprecated Jackson2 variant -- Boot 4 ships tools.jackson. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Declared explicitly rather than left to Boot's auto-configured template. Boot's
     * RabbitTemplateConfigurer only applies a MessageConverter bean via ifUnique(...), so the day a
     * second converter bean appears on the context the outgoing RoadmapUpdatedEvent would silently
     * fall back to Java serialization. Setting it here makes the JSON contract independent of how
     * many converter beans exist.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
