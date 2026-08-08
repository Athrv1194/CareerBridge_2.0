package com.careerbridge.resume.messaging;

import com.careerbridge.resume.config.RabbitMQConfig;
import com.careerbridge.resume.event.ResumeGeneratedEvent;
import com.careerbridge.resume.model.StudentResume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Fail-soft, like every other publisher in this project. The resume row and its PDF bytes are
 * already written by the time this runs, so rethrowing on a broker outage would report failure for
 * work that actually happened -- and the student would retry, generating a second identical resume
 * at version+1. The cost of a swallowed publish is that prs-service's resumeScore and
 * student-service's resumeUrl lag until the next generation, which is recoverable; a failed
 * request for a resume that exists is not.
 */
@Component
public class ResumeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResumeEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ResumeEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishResumeGenerated(StudentResume resume) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.RESUME_GENERATED_ROUTING_KEY,
                    ResumeGeneratedEvent.builder()
                            .resumeId(resume.getId())
                            .studentId(resume.getStudentId())
                            .atsScore(resume.getAtsScore())
                            .version(resume.getVersion())
                            .generatedAt(resume.getGeneratedAt())
                            .build());

            log.info("Published {} for studentId={} version={} atsScore={}",
                    RabbitMQConfig.RESUME_GENERATED_ROUTING_KEY,
                    resume.getStudentId(), resume.getVersion(), resume.getAtsScore());
        } catch (Exception ex) {
            log.warn("Failed to publish {} for studentId={}: {}",
                    RabbitMQConfig.RESUME_GENERATED_ROUTING_KEY, resume.getStudentId(), ex.getMessage());
        }
    }
}
