package com.careerbridge.assessment.config;

import com.careerbridge.assessment.constants.AssessmentConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    /**
     * Producer side only declares the exchange; consumers own their own queues and bindings.
     *
     * durable=true, autoDelete=false must match auth-service's and student-service's declaration
     * exactly. A mismatch is answered with 406 PRECONDITION_FAILED, and because declaration happens
     * asynchronously on the connection callback, publishing would fail quietly rather than loudly.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(AssessmentConstants.EXCHANGE_NAME, true, false);
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
