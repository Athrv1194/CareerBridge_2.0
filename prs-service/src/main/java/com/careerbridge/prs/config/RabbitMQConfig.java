package com.careerbridge.prs.config;

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
 * Five consumer queues and one publisher -- the most connected service in CareerBridge, since the
 * Placement Readiness Score is a composite of what five other services produce.
 *
 * One queue per event type, never one queue with multiple listeners. Two @RabbitListener methods on
 * a single queue create two containers consuming it and RabbitMQ round-robins between them, so
 * roughly half of each event type binds into the wrong class. Jackson 3 has
 * FAIL_ON_UNKNOWN_PROPERTIES off, so that does not throw -- it yields an all-null object a null
 * guard silently discards, and half the score updates vanish with no error anywhere. This is
 * notification-service's logged lesson, and it costs four queues to avoid.
 *
 * There is deliberately no custom SimpleRabbitListenerContainerFactory. TypePrecedence.INFERRED is
 * already Spring AMQP's default, and Boot injects the single MessageConverter bean below into the
 * auto-configured factory -- declaring one would restate the default. What actually makes INFERRED
 * work is the concrete parameter type on each listener method; see PrsEventConsumer.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "careerbridge.exchange";

    public static final String PRS_USER_QUEUE = "careerbridge.prs.user.queue";
    public static final String PRS_RECOMMENDATION_QUEUE = "careerbridge.prs.recommendation.queue";
    public static final String PRS_ROADMAP_QUEUE = "careerbridge.prs.roadmap.queue";
    public static final String PRS_RESUME_QUEUE = "careerbridge.prs.resume.queue";
    public static final String PRS_MENTOR_QUEUE = "careerbridge.prs.mentor.queue";

    /**
     * "student.registered", NOT "user.registered".
     *
     * auth-service's RabbitMQConfig declares STUDENT_REGISTERED_ROUTING_KEY = "student.registered"
     * and AuthServiceImpl publishes with exactly that; no publisher anywhere in this system emits
     * "user.registered". Binding a topic queue to a key nothing publishes is not an error -- the
     * queue is created, the consumer starts, the broker UI looks healthy, and it receives nothing
     * forever. Every PRS row would then be created by the defensive fallback in
     * PrsServiceImpl.updateAssessmentScore and initialisePrs would be dead code, which looks
     * exactly like the service working. Do not "correct" this back.
     */
    public static final String STUDENT_REGISTERED_ROUTING_KEY = "student.registered";
    public static final String RECOMMENDATION_GENERATED_ROUTING_KEY = "recommendation.generated";
    public static final String ROADMAP_UPDATED_ROUTING_KEY = "roadmap.updated";
    public static final String RESUME_GENERATED_ROUTING_KEY = "resume.generated";

    /**
     * Published by mentor-service when a mentor marks a session COMPLETED. notification-service
     * binds its own queue to this same key to nudge the student for a review -- two consumers, two
     * queues, which is the rule this class already follows four times over.
     */
    public static final String SESSION_COMPLETED_ROUTING_KEY = "session.completed";

    public static final String PRS_UPDATED_ROUTING_KEY = "prs.updated";

    /**
     * durable=true, autoDelete=false must match every other service's declaration of this same
     * exchange exactly. A mismatch is answered with 406 PRECONDITION_FAILED, and because
     * declaration happens asynchronously on the connection callback, the consumers would simply
     * never start rather than failing loudly at boot.
     */
    @Bean
    public TopicExchange careerBridgeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * Durable, like every other consumer queue here: a registration that arrives while this service
     * is restarting must still create the student's PRS row, or they hold no score until their next
     * assessment.
     */
    @Bean
    public Queue prsUserQueue() {
        return new Queue(PRS_USER_QUEUE, true);
    }

    @Bean
    public Queue prsRecommendationQueue() {
        return new Queue(PRS_RECOMMENDATION_QUEUE, true);
    }

    @Bean
    public Queue prsRoadmapQueue() {
        return new Queue(PRS_ROADMAP_QUEUE, true);
    }

    @Bean
    public Queue prsResumeQueue() {
        return new Queue(PRS_RESUME_QUEUE, true);
    }

    @Bean
    public Queue prsMentorQueue() {
        return new Queue(PRS_MENTOR_QUEUE, true);
    }

    @Bean
    public Binding prsStudentRegisteredBinding(Queue prsUserQueue, TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(prsUserQueue)
                .to(careerBridgeExchange)
                .with(STUDENT_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public Binding prsRecommendationGeneratedBinding(Queue prsRecommendationQueue,
                                                     TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(prsRecommendationQueue)
                .to(careerBridgeExchange)
                .with(RECOMMENDATION_GENERATED_ROUTING_KEY);
    }

    @Bean
    public Binding prsRoadmapUpdatedBinding(Queue prsRoadmapQueue, TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(prsRoadmapQueue)
                .to(careerBridgeExchange)
                .with(ROADMAP_UPDATED_ROUTING_KEY);
    }

    @Bean
    public Binding prsResumeGeneratedBinding(Queue prsResumeQueue, TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(prsResumeQueue)
                .to(careerBridgeExchange)
                .with(RESUME_GENERATED_ROUTING_KEY);
    }

    @Bean
    public Binding prsSessionCompletedBinding(Queue prsMentorQueue, TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(prsMentorQueue)
                .to(careerBridgeExchange)
                .with(SESSION_COMPLETED_ROUTING_KEY);
    }

    /**
     * Only five bindings are declared. prs.updated is published, never consumed here, and a queue
     * bound to it with no listener would accrue every event forever and read as an unprocessed
     * backlog -- the same reason organization-service declares no queue for organization.created
     * and roadmap-service none for roadmap.updated. A future dashboard service declares its own.
     */

    /** JacksonJson (Jackson 3), not the deprecated Jackson2 variant -- Boot 4 ships tools.jackson. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Declared explicitly rather than left to Boot's auto-configured template. Boot's
     * RabbitTemplateConfigurer only applies a MessageConverter bean via ifUnique(...), so the day a
     * second converter bean appears on the context the outgoing PrsUpdatedEvent would silently fall
     * back to Java serialization. Setting it here makes the JSON contract independent of how many
     * converter beans exist.
     *
     * Boot still injects the same converter into the listener container factory for the three
     * consumers, so one bean serves both directions.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
