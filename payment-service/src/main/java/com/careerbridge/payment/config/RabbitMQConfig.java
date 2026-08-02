package com.careerbridge.payment.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publisher side only. This service declares the exchange and nothing else -- no Queue bean, no
 * Binding bean, no @RabbitListener.
 *
 * auth-service owns careerbridge.auth.subscription.queue and declares its own binding, per the
 * project's one-queue-per-consumer-per-event rule. Declaring the consumer's queue here would
 * either duplicate theirs or, if auth-service were ever down at first boot, create a queue with
 * no listener that silently accrues every event forever.
 *
 * TopicExchange(EXCHANGE, true, false) -- durable, not auto-delete. These arguments must match
 * every other service's declaration exactly or RabbitMQ answers 406 PRECONDITION_FAILED and the
 * consumers simply never start rather than failing loudly at boot.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";
    public static final String SUBSCRIPTION_ACTIVATED_ROUTING_KEY = "subscription.activated";

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
     * Declared explicitly rather than relying on Boot's auto-configured template: Boot's
     * RabbitTemplateConfigurer applies a MessageConverter via ifUnique(...), so a second converter
     * bean appearing later would silently drop the outgoing event back to Java serialization.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
