package com.paycore.email;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.paycore.email.dto.EmailStatusDto;
import com.paycore.email.exception.EmailDeliveryFailedException;
import com.paycore.email.exception.EmailDeliveryUnavailableException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Service
public class GmailApiEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailApiEmailService.class);

    private final EmailProperties emailProperties;
    private final GmailOAuthService gmailOAuthService;
    private final EmailTemplateService emailTemplateService;

    public GmailApiEmailService(
            EmailProperties emailProperties,
            GmailOAuthService gmailOAuthService,
            EmailTemplateService emailTemplateService
    ) {
        this.emailProperties = emailProperties;
        this.gmailOAuthService = gmailOAuthService;
        this.emailTemplateService = emailTemplateService;
    }

    @Override
    public boolean isConfigured() {
        return emailProperties.isConfigured();
    }

    @Override
    public EmailStatusDto getStatus() {
        List<String> missing = new ArrayList<>();
        if (emailProperties.getClientId() == null || emailProperties.getClientId().isBlank()) {
            missing.add("GOOGLE_CLIENT_ID");
        }
        if (emailProperties.getClientSecret() == null || emailProperties.getClientSecret().isBlank()) {
            missing.add("GOOGLE_CLIENT_SECRET");
        }
        if (emailProperties.getRefreshToken() == null || emailProperties.getRefreshToken().isBlank()) {
            missing.add("GOOGLE_REFRESH_TOKEN");
        }
        if (emailProperties.getSenderEmail() == null || emailProperties.getSenderEmail().isBlank()) {
            missing.add("GOOGLE_SENDER_EMAIL");
        }

        return new EmailStatusDto(
                isConfigured(),
                emailProperties.getProvider(),
                emailProperties.getSenderEmail(),
                emailProperties.getRedirectUri(),
                missing
        );
    }

    @Override
    public void sendVerificationEmail(String toEmail, String recipientName, String rawToken) {
        ensureConfigured();
        String verificationUrl = buildUrl("/verify-email", "token", rawToken);
        EmailTemplateService.EmailContent content = emailTemplateService.createVerificationEmail(recipientName, verificationUrl, 24);
        sendViaGmailApi(toEmail, recipientName, content);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String recipientName, String rawToken) {
        ensureConfigured();
        String resetUrl = buildUrl("/reset-password", "token", rawToken);
        EmailTemplateService.EmailContent content = emailTemplateService.createPasswordResetEmail(recipientName, resetUrl, 15);
        sendViaGmailApi(toEmail, recipientName, content);
    }

    @Override
    public void sendPasswordChangedNotification(String toEmail, String recipientName) {
        ensureConfigured();
        EmailTemplateService.EmailContent content = emailTemplateService.createPasswordChangedEmail(recipientName);
        sendViaGmailApi(toEmail, recipientName, content);
    }

    @Override
    public void sendAdminSetupEmail(String toEmail, String recipientName, String rawToken) {
        ensureConfigured();
        String setupUrl = buildUrl("/admin-setup", "token", rawToken);
        EmailTemplateService.EmailContent content = emailTemplateService.createAdminSetupEmail(recipientName, setupUrl, 24);
        sendViaGmailApi(toEmail, recipientName, content);
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            EmailStatusDto status = getStatus();
            String missing = String.join(", ", status.getMissingProperties());
            throw new EmailDeliveryUnavailableException(
                    "Real email delivery is unavailable because Google OAuth credentials are not configured. Missing: " + missing
            );
        }
    }

    private String buildUrl(String path, String paramKey, String paramValue) {
        String base = emailProperties.getFrontendUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path + "?" + paramKey + "=" + paramValue;
    }

    protected void sendViaGmailApi(String toEmail, String recipientName, EmailTemplateService.EmailContent content) {
        try {
            String accessToken = gmailOAuthService.getValidAccessToken();

            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);
            MimeMessage mimeMessage = new MimeMessage(session);

            String sender = emailProperties.getSenderEmail();
            mimeMessage.setFrom(new InternetAddress(sender, "PayCore Security"));
            mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(toEmail, recipientName));
            mimeMessage.setSubject(content.getSubject(), "UTF-8");

            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(content.getTextBody(), "UTF-8", "plain");
            multipart.addBodyPart(textPart);

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(content.getHtmlBody(), "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            mimeMessage.setContent(multipart);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            byte[] rawBytes = buffer.toByteArray();
            String encodedRaw = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);

            Message gmailMessage = new Message();
            gmailMessage.setRaw(encodedRaw);

            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            HttpRequestInitializer requestInitializer = request -> request.getHeaders().setAuthorization("Bearer " + accessToken);

            Gmail service = new Gmail.Builder(transport, jsonFactory, requestInitializer)
                    .setApplicationName("PayCore")
                    .build();

            Message sent = service.users().messages().send("me", gmailMessage).execute();
            if (sent == null || sent.getId() == null) {
                throw new EmailDeliveryFailedException("Gmail API did not return a valid message confirmation id.");
            }

            log.info("Email [{}] successfully dispatched via Gmail API to [{}] (MessageId: {})",
                    content.getSubject(), toEmail, sent.getId());
        } catch (EmailDeliveryUnavailableException | EmailDeliveryFailedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to deliver email to [{}] via Gmail API: {}", toEmail, ex.getMessage());
            throw new EmailDeliveryFailedException("Failed to send email via Gmail API: " + ex.getMessage(), ex);
        }
    }
}
