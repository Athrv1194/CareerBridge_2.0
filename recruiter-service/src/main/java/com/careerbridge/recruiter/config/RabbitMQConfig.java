package com.careerbridge.recruiter.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publisher-only, same shape as organization-service's RabbitMQConfig: this service publishes
 * application.submitted and application.status.updated, and nothing in CareerBridge consumes
 * either yet, so no queue is declared here. A queue bound with no listener accrues every event
 * forever and looks like an unprocessed backlog -- the future notification-service consumer
 * declares its own queue when it lands.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";

    public static final String APPLICATION_SUBMITTED_ROUTING_KEY = "application.submitted";
    public static final String APPLICATION_STATUS_UPDATED_ROUTING_KEY = "application.status.updated";

    /**
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
