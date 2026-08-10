package com.careerbridge.mentor.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publisher-only. Three outbound routing keys, and deliberately NO queue.
 *
 * A queue belongs to the consumer that reads it, never to the publisher. prs-service declares
 * careerbridge.prs.mentor.queue for session.completed, and notification-service declares its own
 * three; declaring any of them here would either duplicate theirs or, for session.booked and
 * session.accepted, create a second consumer that round-robins against notification-service and
 * steals half its events. Same rule organization-service, roadmap-service, recruiter-service and
 * resume-service all follow.
 */
@Configuration
public class MentorRabbitMQConfig {

    /** Must match every other service's literal exactly. */
    public static final String EXCHANGE = "careerbridge.exchange";

    public static final String SESSION_BOOKED_ROUTING_KEY = "session.booked";
    public static final String SESSION_ACCEPTED_ROUTING_KEY = "session.accepted";
    public static final String SESSION_COMPLETED_ROUTING_KEY = "session.completed";

    /**
     * durable=true, autoDelete=false must match every other service's declaration of this same
     * exchange exactly. A mismatch is answered with 406 PRECONDITION_FAILED, and because
     * declaration happens asynchronously on the connection callback, publishing would simply fail
     * in the background rather than failing loudly at boot.
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
     * Declared explicitly rather than left to Boot's auto-configured template, matching
     * prs-service. Boot's RabbitTemplateConfigurer applies a MessageConverter bean only via
     * ifUnique(...), so the day a second converter bean appears on the context these events would
     * silently fall back to Java serialization and every consumer would break at once.
     *
     * Named rabbitTemplate, and the converter jsonMessageConverter -- never after a
     * component-scanned class. A @Bean method sharing a scanned bean's name is a
     * BeanDefinitionOverrideException at startup that no unit test can see; recruiter-service lost
     * a session to exactly that, 2026-08-01.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
