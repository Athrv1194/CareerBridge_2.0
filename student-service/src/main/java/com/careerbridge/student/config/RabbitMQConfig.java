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
     * A second, distinct queue for resume.generated -- never a second listener on STUDENT_QUEUE.
     * Two @RabbitListener methods sharing one queue create two containers RabbitMQ round-robins
     * between, so roughly half of each event type would bind into the wrong method. Jackson 3 has
     * FAIL_ON_UNKNOWN_PROPERTIES off, so that would not throw; it would silently null out half the
     * resumeUrl updates. This is notification-service's logged two-queues lesson, applied here for
     * the same reason prs-service uses four separate queues rather than one shared one.
     */
    public static final String STUDENT_RESUME_QUEUE = "careerbridge.student.resume.queue";
    public static final String RESUME_GENERATED_ROUTING_KEY = "resume.generated";

    /** A third queue, per the same one-queue-per-event-type rule as the two above. */
    public static final String STUDENT_DEPARTMENT_QUEUE = "careerbridge.student.department.queue";
    public static final String USER_DEPARTMENT_UPDATED_ROUTING_KEY = "user.department.updated";

    /**
     * Durable so queued registrations survive a broker restart -- the profile would otherwise
     * never be created and the student would land in the app with no profile row.
     */
    @Bean
    public Queue studentQueue() {
        return new Queue(STUDENT_QUEUE, true);
    }

    @Bean
    public Queue studentResumeQueue() {
        return new Queue(STUDENT_RESUME_QUEUE, true);
    }

    @Bean
    public Queue studentDepartmentQueue() {
        return new Queue(STUDENT_DEPARTMENT_QUEUE, true);
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

    @Bean
    public Binding resumeGeneratedBinding(Queue studentResumeQueue, TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(studentResumeQueue)
                .to(careerBridgeExchange)
                .with(RESUME_GENERATED_ROUTING_KEY);
    }

    @Bean
    public Binding userDepartmentUpdatedBinding(Queue studentDepartmentQueue,
                                                TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(studentDepartmentQueue)
                .to(careerBridgeExchange)
                .with(USER_DEPARTMENT_UPDATED_ROUTING_KEY);
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
