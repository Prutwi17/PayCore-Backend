package com.paycore.email;

import com.paycore.email.dto.EmailStatusDto;
import com.paycore.email.exception.EmailDeliveryFailedException;
import com.paycore.email.exception.EmailDeliveryUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailApiEmailServiceTest {

    @Mock
    private GmailOAuthService gmailOAuthService;

    @Mock
    private EmailTemplateService emailTemplateService;

    private EmailProperties emailProperties;
    private GmailApiEmailService emailService;

    @BeforeEach
    void setUp() {
        emailProperties = new EmailProperties();
        emailProperties.setProvider("gmail");
        emailProperties.setClientId("test-client-id");
        emailProperties.setClientSecret("test-client-secret");
        emailProperties.setRefreshToken("test-refresh-token");
        emailProperties.setSenderEmail("omp27614@gmail.com");
        emailProperties.setFrontendUrl("http://localhost:5173");

        emailService = new GmailApiEmailService(emailProperties, gmailOAuthService, emailTemplateService);
    }

    @Test
    @DisplayName("Email service should report configured when all required properties are present")
    void testIsConfiguredTrue() {
        assertThat(emailService.isConfigured()).isTrue();

        EmailStatusDto status = emailService.getStatus();
        assertThat(status.isConfigured()).isTrue();
        assertThat(status.getMissingProperties()).isEmpty();
        assertThat(status.getSenderEmail()).isEqualTo("omp27614@gmail.com");
    }

    @Test
    @DisplayName("Email service should report unconfigured and list missing properties when credentials missing")
    void testIsConfiguredFalseWhenMissing() {
        emailProperties.setRefreshToken(null);
        emailProperties.setClientId("");

        assertThat(emailService.isConfigured()).isFalse();

        EmailStatusDto status = emailService.getStatus();
        assertThat(status.isConfigured()).isFalse();
        assertThat(status.getMissingProperties()).contains("GOOGLE_CLIENT_ID", "GOOGLE_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("Sending verification email when OAuth unconfigured should throw EmailDeliveryUnavailableException")
    void testSendVerificationEmailWhenUnconfigured() {
        emailProperties.setRefreshToken(null);

        assertThatThrownBy(() -> emailService.sendVerificationEmail("user@example.com", "User", "raw_token"))
                .isInstanceOf(EmailDeliveryUnavailableException.class)
                .hasMessageContaining("Google OAuth credentials are not configured");
    }

    @Test
    @DisplayName("Sending password reset email when OAuth unconfigured should throw EmailDeliveryUnavailableException")
    void testSendPasswordResetEmailWhenUnconfigured() {
        emailProperties.setClientSecret("");

        assertThatThrownBy(() -> emailService.sendPasswordResetEmail("user@example.com", "User", "raw_token"))
                .isInstanceOf(EmailDeliveryUnavailableException.class)
                .hasMessageContaining("Google OAuth credentials are not configured");
    }

    @Test
    @DisplayName("Sending admin setup email should construct URL and delegate template generation")
    void testSendAdminSetupEmailDelegates() {
        // Subclass to mock actual network call to Gmail API
        GmailApiEmailService testableService = new GmailApiEmailService(emailProperties, gmailOAuthService, emailTemplateService) {
            @Override
            protected void sendViaGmailApi(String toEmail, String recipientName, EmailTemplateService.EmailContent content) {
                // Mocked successful send for unit test
            }
        };

        when(emailTemplateService.createAdminSetupEmail(anyString(), anyString(), anyInt()))
                .thenReturn(new EmailTemplateService.EmailContent("Subject", "<h1>Html</h1>", "Text"));

        testableService.sendAdminSetupEmail("omp27614@gmail.com", "Platform Administrator", "token123");

        verify(emailTemplateService, times(1))
                .createAdminSetupEmail(eq("Platform Administrator"), contains("/admin-setup?token=token123"), eq(24));
    }

    @Test
    @DisplayName("OAuth token refresh failure should propagate as EmailDeliveryFailedException")
    void testOAuthTokenRefreshFailure() {
        when(emailTemplateService.createVerificationEmail(anyString(), anyString(), anyInt()))
                .thenReturn(new EmailTemplateService.EmailContent("Subject", "<h1>Html</h1>", "Text"));

        when(gmailOAuthService.getValidAccessToken())
                .thenThrow(new EmailDeliveryFailedException("Google OAuth token refresh rejected with HTTP status: 401"));

        assertThatThrownBy(() -> emailService.sendVerificationEmail("user@example.com", "User", "token"))
                .isInstanceOf(EmailDeliveryFailedException.class)
                .hasMessageContaining("token refresh");
    }
}
