package com.careerbridge.auth.config;

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

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";
    public static final String STUDENT_REGISTERED_ROUTING_KEY = "student.registered";

    /** Consumed from payment-service. This service's first inbound event. */
    public static final String SUBSCRIPTION_QUEUE = "careerbridge.auth.subscription.queue";
    public static final String SUBSCRIPTION_ACTIVATED_ROUTING_KEY = "subscription.activated";

    /**
     * This service publishes student.registered and consumes subscription.activated. It declares
     * the exchange plus its OWN queue and binding -- payment-service, the publisher, deliberately
     * declares no queue, per the project's one-queue-per-consumer-per-event rule.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * Durable, non-exclusive, non-auto-delete -- new Queue(name, true) is the same shape every
     * other consumer queue in this project uses. The durable/autoDelete arguments on the exchange
     * above must stay identical across all services or RabbitMQ answers 406 PRECONDITION_FAILED
     * and the consumer silently never starts.
     */
    @Bean
    public Queue authSubscriptionQueue() {
        return new Queue(SUBSCRIPTION_QUEUE, true);
    }

    @Bean
    public Binding subscriptionActivatedBinding(Queue authSubscriptionQueue,
                                                TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(authSubscriptionQueue)
                .to(careerBridgeExchange)
                .with(SUBSCRIPTION_ACTIVATED_ROUTING_KEY);
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
