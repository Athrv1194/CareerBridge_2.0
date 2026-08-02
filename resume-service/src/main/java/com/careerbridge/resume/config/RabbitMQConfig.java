package com.careerbridge.resume.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publisher-only, same shape as organization-service's and recruiter-service's configs: this
 * service publishes resume.generated and consumes nothing.
 *
 * No queue and no binding are declared here even though resume.generated DOES have consumers --
 * prs-service and student-service each declare their own queue for it, per the project's
 * one-queue-per-consumer-per-event rule. A queue declared on the publisher side would either
 * duplicate theirs or, worse, sit unconsumed and accrue every event forever.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";

    public static final String RESUME_GENERATED_ROUTING_KEY = "resume.generated";

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
