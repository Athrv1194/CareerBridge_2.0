package com.careerbridge.student.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";
    public static final String STUDENT_QUEUE = "careerbridge.student.queue";
    public static final String STUDENT_REGISTERED_ROUTING_KEY = "student.registered";

    /**
     * Durable so queued registrations survive a broker restart -- the profile would otherwise
     * never be created and the student would land in the app with no profile row.
     */
    @Bean
    public Queue studentQueue() {
        return new Queue(STUDENT_QUEUE, true);
    }

    /**
     * durable=true, autoDelete=false must match auth-service's declaration exactly. A mismatch is
     * answered with 406 PRECONDITION_FAILED, and because declaration happens asynchronously on the
     * connection callback, the consumer would simply never start rather than failing loudly.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Binding studentRegisteredBinding(Queue studentQueue, TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(studentQueue).to(careerBridgeExchange).with(STUDENT_REGISTERED_ROUTING_KEY);
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
