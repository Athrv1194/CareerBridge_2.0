package com.careerbridge.organization.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";
    public static final String ORGANIZATION_CREATED_ROUTING_KEY = "organization.created";

    /** Published on approve(), consumed by auth-service to provision the ORG_ADMIN user. */
    public static final String ORGANIZATION_REQUEST_APPROVED_ROUTING_KEY = "organization.request.approved";

    /**
     * Producer side only declares the exchange; consumers own their own queues and bindings.
     *
     * Deliberately no careerbridge.organization.queue here. Nothing consumes organization.created
     * yet, and a queue bound with no listener is not harmless: it accrues every event forever and
     * shows as a growing backlog no one is draining.
     *
     * durable=true, autoDelete=false must match every other service's declaration of this same
     * exchange exactly, or the broker answers 406 PRECONDITION_FAILED.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /** JacksonJson (Jackson 3), not the deprecated Jackson2 variant; Boot 4 ships tools.jackson. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
