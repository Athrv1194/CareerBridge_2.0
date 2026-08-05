package com.careerbridge.notification.service;

import com.careerbridge.notification.constants.NotificationConstants;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Sends the "your recommendation is ready" email over Gmail SMTP.
 *
 * A concrete class with no interface, deliberately: there is exactly one implementation and
 * Mockito mocks classes perfectly well, so an XService/XServiceImpl pair here would be a second
 * file that never has a second implementation.
 *
 * Every method is fail-soft and returns a boolean rather than throwing. A dead SMTP server must
 * never cost the student their notification -- the in-app record is written either way, and the
 * NotificationRecord captures the failure reason for an operator.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    /**
     * The authenticated Gmail identity, not a separate constant.
     *
     * Gmail rejects any From that is not the authenticated user or a verified alias (553), so
     * deriving it from spring.mail.username makes a mismatch structurally impossible. Omitting
     * setFrom entirely is not an option either: Jakarta Mail then synthesises something like
     * user@HOSTNAME from the local machine, which Gmail also rejects.
     */
    private final String fromEmail;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * @return true if the message was handed to the SMTP server, false on any failure.
     */
    public boolean sendRecommendationEmail(String toEmail,
                                           String studentName,
                                           String topCareerName,
                                           Double matchPercentage,
                                           Long recommendationId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(NotificationConstants.EMAIL_SUBJECT);
            helper.setText(buildHtmlBody(studentName, topCareerName, matchPercentage), true);

            mailSender.send(message);

            log.info("Sent recommendation email to {} for recommendationId={}", toEmail, recommendationId);
            return true;
        } catch (Exception ex) {
            // Fail-soft: the caller records this as a FAILED NotificationRecord and carries on.
            // Rethrowing would abort the whole event handler and cost the in-app notification too.
            log.error("Failed to send recommendation email to {} for recommendationId={}: {}",
                    toEmail, recommendationId, ex.getMessage());
            return false;
        }
    }

    /**
     * @return true if the message was handed to the SMTP server, false on any failure.
     */
    public boolean sendPasswordResetOtpEmail(String toEmail, String firstName, String otp, int expiresInMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(NotificationConstants.PASSWORD_RESET_EMAIL_SUBJECT);
            helper.setText(buildPasswordResetOtpBody(firstName, otp, expiresInMinutes), true);

            mailSender.send(message);

            log.info("Sent password reset OTP email to {}", toEmail);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send password reset OTP email to {}: {}", toEmail, ex.getMessage());
            return false;
        }
    }

    /**
     * @return true if the message was handed to the SMTP server, false on any failure.
     */
    public boolean sendPasswordChangedEmail(String toEmail, String firstName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(NotificationConstants.PASSWORD_CHANGED_EMAIL_SUBJECT);
            helper.setText(buildPasswordChangedBody(firstName), true);

            mailSender.send(message);

            log.info("Sent password changed confirmation email to {}", toEmail);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send password changed confirmation email to {}: {}", toEmail, ex.getMessage());
            return false;
        }
    }

    /** Public for the same test-reachability reason as buildHtmlBody above. */
    public String buildPasswordResetOtpBody(String firstName, String otp, int expiresInMinutes) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : firstName;

        return String.format(Locale.ROOT, """
                <html>
                  <body style="font-family: Arial, Helvetica, sans-serif; color: #1f2933; line-height: 1.6;">
                    <h2 style="color: #1c6ea4; margin-bottom: 4px;">Reset your password</h2>
                    <p>Hi %s,</p>
                    <p>Use this code to reset your CareerBridge password. It expires in %d minutes.</p>
                    <p style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #1c6ea4; margin: 24px 0;">
                      %s
                    </p>
                    <p>If you didn't request this, you can safely ignore this email -- your password will not change.</p>
                    <p style="margin-top: 24px;">The CareerBridge Team</p>
                  </body>
                </html>
                """, name, expiresInMinutes, otp);
    }

    /** Public for the same test-reachability reason as buildHtmlBody above. */
    public String buildPasswordChangedBody(String firstName) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : firstName;

        return String.format(Locale.ROOT, """
                <html>
                  <body style="font-family: Arial, Helvetica, sans-serif; color: #1f2933; line-height: 1.6;">
                    <h2 style="color: #1c6ea4; margin-bottom: 4px;">Your password was changed</h2>
                    <p>Hi %s,</p>
                    <p>This is a confirmation that your CareerBridge password was just changed.</p>
                    <p style="font-weight: bold; color: #b71c1c;">
                      If this wasn't you, please reset your password again immediately and contact support.
                    </p>
                    <p style="margin-top: 24px;">The CareerBridge Team</p>
                  </body>
                </html>
                """, name);
    }

    /**
     * Public so the body can be asserted directly in a test without going through a MimeMessage
     * built on a null Session. (Package-private would be the tighter choice, but every test class
     * in this project lives in the base package com.careerbridge.notification rather than mirroring
     * the source packages, so it would be unreachable. A pure formatting function with no side
     * effects costs nothing to expose.)
     *
     * matchPercentage is null-coalesced here as a second line of defence -- the caller already
     * does it, but "%.1f" on a null Double throws NPE, and inside this class that NPE would be
     * swallowed by the fail-soft catch above and degrade to a silent "no email sent".
     *
     * Locale.ROOT so the decimal separator is a dot on every machine; a comma-decimal locale
     * would otherwise render "46,7%".
     */
    public String buildHtmlBody(String studentName, String topCareerName, Double matchPercentage) {
        String name = (studentName == null || studentName.isBlank()) ? "there" : studentName;
        String career = (topCareerName == null) ? "your recommended career path" : topCareerName;
        String match = (matchPercentage == null)
                ? "n/a"
                : String.format(Locale.ROOT, "%.1f%%", matchPercentage);

        return String.format(Locale.ROOT, """
                <html>
                  <body style="font-family: Arial, Helvetica, sans-serif; color: #1f2933; line-height: 1.6;">
                    <h2 style="color: #1c6ea4; margin-bottom: 4px;">Your Career Recommendation is Ready</h2>
                    <p>Hi %s,</p>
                    <p>
                      We have finished analysing your assessment. Based on your results, your strongest
                      match is:
                    </p>
                    <p style="font-size: 18px; font-weight: bold; color: #1c6ea4; margin: 16px 0;">
                      %s &mdash; %s match
                    </p>
                    <p>
                      This is a starting point, not a verdict. Sign in to CareerBridge to see your full
                      ranking, why each career was suggested, and the skills to focus on next.
                    </p>
                    <p style="margin-top: 24px;">Good luck,<br/>The CareerBridge Team</p>
                    <hr style="border: none; border-top: 1px solid #e4e7eb; margin-top: 28px;"/>
                    <p style="font-size: 12px; color: #7b8794;">
                      You are receiving this because you completed a CareerBridge assessment.
                    </p>
                  </body>
                </html>
                """, name, career, match);
    }
}
