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

    /**
     * Published, deliberately unconsumed by any queue declared here -- notification-service owns
     * both queues, same one-queue-per-consumer-per-event rule as every other cross-service event
     * in this project.
     */
    public static final String PASSWORD_RESET_REQUESTED_ROUTING_KEY = "password.reset.requested";
    public static final String PASSWORD_CHANGED_ROUTING_KEY = "password.changed";

    /** Consumed from payment-service. This service's first inbound event. */
    public static final String SUBSCRIPTION_QUEUE = "careerbridge.auth.subscription.queue";
    public static final String SUBSCRIPTION_ACTIVATED_ROUTING_KEY = "subscription.activated";

    /** Consumed from organization-service, on approve(). Provisions the ORG_ADMIN user. */
    public static final String ORGANIZATION_QUEUE = "careerbridge.auth.organization.queue";
    public static final String ORGANIZATION_REQUEST_APPROVED_ROUTING_KEY = "organization.request.approved";

    /**
     * Published, deliberately unconsumed by any queue declared here -- notification-service owns
     * the queue, same one-queue-per-consumer-per-event rule as every other cross-service event.
     */
    public static final String ORG_ADMIN_INVITED_ROUTING_KEY = "organization.admin.invited";

    /**
     * Published whenever a user's department is set, changed or cleared. student-service owns the
     * queue and keeps a local copy on StudentProfile, which is what puts department on the public
     * candidate profile recruiter-service searches.
     *
     * An event rather than a synchronous read, and not by preference: auth-service is the only
     * backend service with Spring Security on its classpath, and its chain ends in
     * .anyRequest().authenticated() -- so a service-to-service GET carrying only gateway-style
     * headers is answered 401, with no JWT to present. Nothing else in this system calls
     * auth-service synchronously for exactly that reason. Same event-plus-local-copy shape as
     * resume.generated -> StudentProfile.resumeUrl.
     */
    public static final String USER_DEPARTMENT_UPDATED_ROUTING_KEY = "user.department.updated";

    /**
     * This service publishes student.registered and organization.admin.invited, and consumes
     * subscription.activated and organization.request.approved. It declares the exchange plus its
     * OWN queues and bindings for what it consumes -- the publishers of those events deliberately
     * declare no queue, per the project's one-queue-per-consumer-per-event rule.
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

    /**
     * A second queue, never a second @RabbitListener on authSubscriptionQueue above -- two
     * containers on one queue make RabbitMQ round-robin between them, misrouting roughly half of
     * each event type.
     */
    @Bean
    public Queue authOrganizationQueue() {
        return new Queue(ORGANIZATION_QUEUE, true);
    }

    @Bean
    public Binding organizationRequestApprovedBinding(Queue authOrganizationQueue,
                                                       TopicExchange careerBridgeExchange) {
        return BindingBuilder.bind(authOrganizationQueue)
                .to(careerBridgeExchange)
                .with(ORGANIZATION_REQUEST_APPROVED_ROUTING_KEY);
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
