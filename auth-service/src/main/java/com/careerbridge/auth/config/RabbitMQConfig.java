package com.careerbridge.auth.config;

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
    public static final String STUDENT_REGISTERED_ROUTING_KEY = "student.registered";

    /** Producer side only declares the exchange; consumers own their own queues and bindings. */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /** JacksonJson (Jackson 3), not the deprecated Jackson2 variant -- Boot 4 ships tools.jackson. */
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
