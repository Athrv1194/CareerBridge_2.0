package com.careerbridge.student.consumer;

import com.careerbridge.student.config.RabbitMQConfig;
import com.careerbridge.student.event.StudentRegisteredEvent;
import com.careerbridge.student.model.StudentProfile;
import com.careerbridge.student.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StudentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StudentEventConsumer.class);

    private final StudentProfileRepository studentProfileRepository;

    public StudentEventConsumer(StudentProfileRepository studentProfileRepository) {
        this.studentProfileRepository = studentProfileRepository;
    }

    /**
     * Creates the empty profile a student lands on straight after registering, so they never have
     * to "create a profile" as a separate step.
     *
     * The parameter is the concrete event type on purpose: Spring AMQP's default
     * TypePrecedence.INFERRED resolves the payload from this signature, so the sender's __TypeId__
     * header (which names auth-service's package) is never read. Widening this to Object or Message
     * would fall back to that header and blow up with ClassNotFoundException.
     *
     * Not @Transactional. The existsByUserId guard is a best-effort fast path with an unavoidable
     * TOCTOU window; the unique constraint on StudentProfile.userId is what actually guarantees
     * idempotency, and it surfaces here as the DataIntegrityViolationException caught below. A
     * transaction would also mean this fail-soft catch swallows an exception that has already
     * marked the transaction rollback-only.
     */
    @RabbitListener(queues = RabbitMQConfig.STUDENT_QUEUE)
    public void onStudentRegistered(StudentRegisteredEvent event) {
        try {
            if (event == null || event.getUserId() == null) {
                log.warn("Ignoring {} with no userId", RabbitMQConfig.STUDENT_REGISTERED_ROUTING_KEY);
                return;
            }

            if (Boolean.TRUE.equals(studentProfileRepository.existsByUserId(event.getUserId()))) {
                log.info("Profile already exists for userId={}, skipping", event.getUserId());
                return;
            }

            studentProfileRepository.save(StudentProfile.builder()
                    .userId(event.getUserId())
                    .email(event.getEmail())
                    .firstName(event.getFirstName())
                    .lastName(event.getLastName())
                    // Stored so getPublicProfiles can return only STUDENT profiles. This event is
                    // published for every registration regardless of role, so a profile is created
                    // for recruiters and admins too -- the role is the only thing that tells them
                    // apart afterwards.
                    .role(event.getRole())
                    .profileCompletionPercentage(0)
                    .build());

            log.info("Created empty student profile for userId={}", event.getUserId());
        } catch (Exception ex) {
            // Fail-soft: rethrowing would requeue the message and spin the listener forever on a
            // payload that will never succeed. Losing a profile row is recoverable by hand; an
            // infinite redelivery loop takes the consumer down for every other student too.
            log.error("Failed to handle {} for userId={}: {}",
                    RabbitMQConfig.STUDENT_REGISTERED_ROUTING_KEY,
                    event == null ? null : event.getUserId(),
                    ex.getMessage());
        }
    }
}
