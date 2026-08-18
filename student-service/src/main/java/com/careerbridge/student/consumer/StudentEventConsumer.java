package com.careerbridge.student.consumer;

import com.careerbridge.student.config.RabbitMQConfig;
import com.careerbridge.student.event.ResumeGeneratedEvent;
import com.careerbridge.student.event.UserDepartmentUpdatedEvent;
import com.careerbridge.student.event.StudentRegisteredEvent;
import com.careerbridge.student.model.StudentProfile;
import com.careerbridge.student.repository.StudentProfileRepository;
import com.careerbridge.student.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StudentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StudentEventConsumer.class);

    private final StudentProfileRepository studentProfileRepository;
    private final StudentService studentService;

    public StudentEventConsumer(StudentProfileRepository studentProfileRepository,
                                StudentService studentService) {
        this.studentProfileRepository = studentProfileRepository;
        this.studentService = studentService;
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

    /**
     * Fills in StudentProfile.resumeUrl, worth 15% of profile completion in
     * ProfileCompletionCalculator -- nothing wrote this field before resume-service existed, so
     * every student was permanently capped at 85%.
     *
     * Routed through StudentService.updateResumeUrl rather than touching the repository directly:
     * the write needs recalculate()'s private path, not a bare field set, or the completion
     * percentage would silently desync from the profile the moment resumeUrl changed.
     *
     * Deliberately not @Transactional here, matching onStudentRegistered -- the transaction lives
     * inside updateResumeUrl, which is proxied through the Spring-managed StudentService bean.
     *
     * Accepted edge case: deleting the student's last resume in resume-service does not clear
     * resumeUrl here, since there is no resume.deleted event. A student who deletes their only
     * resume keeps the 15% and a URL that now 404s until they generate a new one.
     */
    @RabbitListener(queues = RabbitMQConfig.STUDENT_RESUME_QUEUE)
    public void onResumeGenerated(ResumeGeneratedEvent event) {
        try {
            if (event == null || event.getStudentId() == null || event.getResumeId() == null) {
                log.warn("Ignoring {} with no studentId or resumeId",
                        RabbitMQConfig.RESUME_GENERATED_ROUTING_KEY);
                return;
            }

            studentService.updateResumeUrl(event.getStudentId(),
                    "/api/resume/download/" + event.getResumeId());

            log.info("Set resumeUrl for userId={} from resumeId={}",
                    event.getStudentId(), event.getResumeId());
        } catch (Exception ex) {
            log.error("Failed to handle {} for studentId={}: {}",
                    RabbitMQConfig.RESUME_GENERATED_ROUTING_KEY,
                    event == null ? null : event.getStudentId(),
                    ex.getMessage());
        }
    }

    /**
     * Keeps the local copy of auth-service's department current, which is what puts department on
     * the public candidate profile recruiter-service filters on.
     *
     * A null department is APPLIED, not skipped: clearing a department is a real transition, and
     * treating null as "nothing to do" would leave this service holding a value auth-service no
     * longer has -- a recruiter would keep seeing a candidate under a department they were removed
     * from. That is the opposite of the usual null-guard in the two consumers above, so only
     * userId is guarded here.
     *
     * The event carries the absolute current value rather than a delta, so a redelivery re-applies
     * the same assignment and is harmless -- same rule as prs-service's session-count consumer.
     *
     * Deliberately not @Transactional here, matching the two consumers above: the transaction lives
     * inside updateDepartment, on the proxied StudentService bean.
     */
    @RabbitListener(queues = RabbitMQConfig.STUDENT_DEPARTMENT_QUEUE)
    public void onUserDepartmentUpdated(UserDepartmentUpdatedEvent event) {
        try {
            if (event == null || event.getUserId() == null) {
                log.warn("Ignoring {} with no userId",
                        RabbitMQConfig.USER_DEPARTMENT_UPDATED_ROUTING_KEY);
                return;
            }

            studentService.updateDepartment(event.getUserId(), event.getDepartment());

            log.info("Set department={} for userId={}", event.getDepartment(), event.getUserId());
        } catch (Exception ex) {
            log.error("Failed to handle {} for userId={}: {}",
                    RabbitMQConfig.USER_DEPARTMENT_UPDATED_ROUTING_KEY,
                    event == null ? null : event.getUserId(),
                    ex.getMessage());
        }
    }
}
