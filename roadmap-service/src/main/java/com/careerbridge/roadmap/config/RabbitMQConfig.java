package com.careerbridge.roadmap.config;

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
 * This service both consumes (recommendation.generated) and publishes (roadmap.updated), so it needs
 * the consumer half -- queue and binding -- and the producer half -- an explicit RabbitTemplate.
 * Same shape as recommendation-service's config, not student-service's consume-only one.
 *
 * There is deliberately no custom SimpleRabbitListenerContainerFactory here. TypePrecedence.INFERRED
 * is already Spring AMQP's default, and Boot injects the single MessageConverter bean below into the
 * auto-configured factory -- so declaring one would restate the default and buy nothing. What
 * actually makes INFERRED work is the concrete parameter type on RoadmapEventConsumer's listener
 * method; see the comment there.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";
    public static final String ROADMAP_QUEUE = "careerbridge.roadmap.queue";
    public static final String RECOMMENDATION_GENERATED_ROUTING_KEY = "recommendation.generated";
    public static final String ROADMAP_UPDATED_ROUTING_KEY = "roadmap.updated";

    /**
     * This service's own queue, never notification-service's careerbridge.notification.queue --
     * two services sharing one queue means RabbitMQ round-robins the events between them and each
     * sees roughly half. Durable so a queued recommendation survives a broker restart; otherwise
     * the student gets a recommendation and simply never gets the roadmap that follows from it.
     */
    @Bean
    public Queue roadmapQueue() {
        return new Queue(ROADMAP_QUEUE, true);
    }

    /**
     * durable=true, autoDelete=false must match every other service's declaration of this same
     * exchange exactly. A mismatch is answered with 406 PRECONDITION_FAILED, and because declaration
     * happens asynchronously on the connection callback, the consumer would simply never start
     * rather than failing loudly at boot.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * Only one binding is declared. roadmap.updated is published, never consumed here, and a queue
     * bound to it with no listener would accrue every event forever and read as an unprocessed
     * backlog -- the same reason organization-service declares no queue for organization.created.
     * A future placement-service declares its own.
     */
    @Bean
    public Binding recommendationGeneratedBinding(Queue roadmapQueue, TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(roadmapQueue)
                .to(careerBridgeExchange)
                .with(RECOMMENDATION_GENERATED_ROUTING_KEY);
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
     *
     * Boot still injects the same converter into the listener container factory for the consumer
     * side, so one bean serves both directions.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
