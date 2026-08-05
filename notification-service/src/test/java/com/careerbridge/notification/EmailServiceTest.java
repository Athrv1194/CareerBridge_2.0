package com.careerbridge.notification;

import com.careerbridge.notification.constants.NotificationConstants;
import com.careerbridge.notification.service.EmailService;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JavaMailSender is mocked throughout -- no test here opens an SMTP connection or sends a real
 * email. The MimeMessage is built on a null Session, which is enough to set and read headers.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String FROM = "atharva.pawar.cmfeb26@gmail.com";
    private static final String TO = "ada@careerbridge.com";

    @Mock private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        // Constructed by hand rather than @InjectMocks: the second constructor argument is a
        // @Value-injected String, not a mock, and this is also what pins the From address.
        emailService = new EmailService(mailSender, FROM);
    }

    /** Fresh message per test; stubbed only in the tests that actually send. */
    private void stubMimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Test
    @DisplayName("send: delivers an HTML message to the recipient with the configured subject")
    void sendRecommendationEmail_Success_SendsHtmlMessageToTheRecipient() throws Exception {
        stubMimeMessage();

        boolean sent = emailService.sendRecommendationEmail(
                TO, "Ada Lovelace", "Backend Developer", 66.666, 7L);

        assertTrue(sent);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();

        assertEquals(NotificationConstants.EMAIL_SUBJECT, message.getSubject());
        assertEquals(TO, message.getAllRecipients()[0].toString());
    }

    @Test
    @DisplayName("send: the From header is the authenticated mail username, never a separate constant")
    void sendRecommendationEmail_FromAddress_IsTheAuthenticatedMailUsername() throws Exception {
        stubMimeMessage();

        emailService.sendRecommendationEmail(TO, "Ada", "Backend Developer", 66.6, 7L);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        // Gmail rejects any From that is not the authenticated user (553), so this must track
        // spring.mail.username rather than a hardcoded address.
        assertEquals(FROM, captor.getValue().getFrom()[0].toString());
    }

    @Test
    @DisplayName("send: an SMTP failure returns false and is never rethrown")
    void sendRecommendationEmail_MailSenderThrows_ReturnsFalseAndDoesNotRethrow() {
        stubMimeMessage();
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        boolean sent = assertDoesNotThrow(() -> emailService.sendRecommendationEmail(
                TO, "Ada", "Backend Developer", 66.6, 7L));

        assertFalse(sent, "caller records this as a FAILED NotificationRecord and carries on");
    }

    @Test
    @DisplayName("body: carries the student name, career and match percentage")
    void buildHtmlBody_CarriesStudentNameCareerAndMatchPercentage() {
        String body = emailService.buildHtmlBody("Ada Lovelace", "Backend Developer", 66.666);

        assertTrue(body.contains("Ada Lovelace"), body);
        assertTrue(body.contains("Backend Developer"), body);
        // Locale.ROOT keeps the separator a dot -- a comma-decimal locale would render "66,7%".
        assertTrue(body.contains("66.7%"), body);
        assertTrue(body.contains("<html>"), "must be HTML, sent with the html flag set");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("body: a null match percentage renders n/a rather than throwing NPE")
    void buildHtmlBody_NullMatchPercentage_RendersNaWithoutThrowing() {
        // Inside EmailService an NPE here would be swallowed by the fail-soft catch and degrade
        // to a silent "no email sent".
        String body = assertDoesNotThrow(
                () -> emailService.buildHtmlBody(null, null, null));

        assertTrue(body.contains("n/a"), body);
        assertTrue(body.contains("Hi there,"), "a missing name falls back to a neutral greeting");
    }

    @Test
    @DisplayName("send OTP: delivers an HTML message with the reset-code subject")
    void sendPasswordResetOtpEmail_Success_SendsHtmlMessageToTheRecipient() throws Exception {
        stubMimeMessage();

        boolean sent = emailService.sendPasswordResetOtpEmail(TO, "Ada", "1234", 10);

        assertTrue(sent);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals(NotificationConstants.PASSWORD_RESET_EMAIL_SUBJECT, captor.getValue().getSubject());
        assertEquals(TO, captor.getValue().getAllRecipients()[0].toString());
    }

    @Test
    @DisplayName("send OTP: an SMTP failure returns false and is never rethrown")
    void sendPasswordResetOtpEmail_MailSenderThrows_ReturnsFalseAndDoesNotRethrow() {
        stubMimeMessage();
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        boolean sent = assertDoesNotThrow(
                () -> emailService.sendPasswordResetOtpEmail(TO, "Ada", "1234", 10));

        assertFalse(sent);
    }

    @Test
    @DisplayName("invoice: with PDF bytes, attaches the invoice as a named part")
    void sendInvoiceEmail_WithBytes_AttachesPdf() throws Exception {
        stubMimeMessage();
        byte[] pdf = {'%', 'P', 'D', 'F'};

        boolean sent = emailService.sendInvoiceEmail(
                TO, "STUDENT_PREMIUM", new BigDecimal("199.00"), "CB-INV-000042", pdf, 42L);

        assertTrue(sent);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();

        assertEquals(NotificationConstants.INVOICE_EMAIL_SUBJECT, message.getSubject());
        assertTrue(message.getContent() instanceof Multipart, "an attached message must be multipart");
        Multipart multipart = (Multipart) message.getContent();

        boolean hasAttachment = false;
        for (int i = 0; i < multipart.getCount(); i++) {
            if ("CB-INV-000042.pdf".equals(multipart.getBodyPart(i).getFileName())) {
                hasAttachment = true;
            }
        }
        assertTrue(hasAttachment, "expected an attachment named CB-INV-000042.pdf");
    }

    /**
     * A failed fetch from payment-service must not cost the confirmation email entirely -- the
     * user was genuinely charged and needs to know it, even without the attachment.
     */
    @Test
    @DisplayName("invoice: with null PDF bytes, still sends the email without an attachment")
    void sendInvoiceEmail_NullBytes_StillSendsWithoutAttachment() throws Exception {
        stubMimeMessage();

        boolean sent = emailService.sendInvoiceEmail(
                TO, "STUDENT_PREMIUM", new BigDecimal("199.00"), "CB-INV-000042", null, 42L);

        assertTrue(sent);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("invoice: an SMTP failure returns false and is never rethrown")
    void sendInvoiceEmail_MailSenderThrows_ReturnsFalseAndDoesNotRethrow() {
        stubMimeMessage();
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        boolean sent = assertDoesNotThrow(() -> emailService.sendInvoiceEmail(
                TO, "STUDENT_PREMIUM", new BigDecimal("199.00"), "CB-INV-000042", null, 42L));

        assertFalse(sent);
    }

    @Test
    @DisplayName("OTP body: carries the name, the code itself, and the expiry")
    void buildPasswordResetOtpBody_CarriesNameCodeAndExpiry() {
        String body = emailService.buildPasswordResetOtpBody("Ada", "1234", 10);

        assertTrue(body.contains("Ada"), body);
        assertTrue(body.contains("1234"), body);
        assertTrue(body.contains("10 minutes"), body);
        assertTrue(body.contains("<html>"), "must be HTML, sent with the html flag set");
    }

    @Test
    @DisplayName("send changed: delivers an HTML message with the password-changed subject")
    void sendPasswordChangedEmail_Success_SendsHtmlMessageToTheRecipient() throws Exception {
        stubMimeMessage();

        boolean sent = emailService.sendPasswordChangedEmail(TO, "Ada");

        assertTrue(sent);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals(NotificationConstants.PASSWORD_CHANGED_EMAIL_SUBJECT, captor.getValue().getSubject());
    }

    @Test
    @DisplayName("changed body: carries the \"if this wasn't you\" warning")
    void buildPasswordChangedBody_CarriesIfThisWasNotYouWarning() {
        String body = emailService.buildPasswordChangedBody("Ada");

        assertTrue(body.contains("Ada"), body);
        assertTrue(body.toLowerCase().contains("wasn't you"), body);
    }

    @Test
    @DisplayName("invoice body: carries the plan, amount and invoice number")
    void buildInvoiceHtmlBody_CarriesPlanAmountAndInvoiceNumber() {
        String body = emailService.buildInvoiceHtmlBody(
                "STUDENT_PREMIUM", new BigDecimal("199.00"), "CB-INV-000042");

        assertTrue(body.contains("STUDENT_PREMIUM"), body);
        assertTrue(body.contains("199.00"), body);
        assertTrue(body.contains("CB-INV-000042"), body);
    }

    @Test
    @DisplayName("invoice body: a null amount renders n/a rather than throwing NPE")
    void buildInvoiceHtmlBody_NullAmount_RendersNaWithoutThrowing() {
        String body = assertDoesNotThrow(
                () -> emailService.buildInvoiceHtmlBody("STUDENT_PREMIUM", null, "CB-INV-000042"));

        assertTrue(body.contains("n/a"), body);
    }
}
