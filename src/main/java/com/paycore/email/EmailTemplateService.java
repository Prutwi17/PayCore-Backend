package com.paycore.email;

import org.springframework.stereotype.Service;

@Service
public class EmailTemplateService {

    public static class EmailContent {
        private final String subject;
        private final String htmlBody;
        private final String textBody;

        public EmailContent(String subject, String htmlBody, String textBody) {
            this.subject = subject;
            this.htmlBody = htmlBody;
            this.textBody = textBody;
        }

        public String getSubject() {
            return subject;
        }

        public String getHtmlBody() {
            return htmlBody;
        }

        public String getTextBody() {
            return textBody;
        }
    }

    public EmailContent createVerificationEmail(String recipientName, String verificationUrl, int expiryHours) {
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : "Valued Partner";
        String subject = "Verify your email address — PayCore Payment Engine";

        String textBody = String.format("""
                Hello %s,

                Thank you for registering with PayCore Payment Processing Engine.
                Please verify your email address by opening the following link in your browser:

                %s

                This verification link will expire in %d hours.
                If you did not register for a PayCore account, please safely ignore this email.

                Sincerely,
                The PayCore Security Team
                """, name, verificationUrl, expiryHours);

        String htmlBody = wrapHtmlTemplate(
                "Verify Your Email Address",
                String.format("""
                        <p style="margin: 0 0 16px 0; font-size: 15px; line-height: 1.6; color: #334155;">Hello <strong>%s</strong>,</p>
                        <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #334155;">
                            Thank you for signing up with <strong>PayCore</strong>. To activate your account and start integrating enterprise payment services, please verify your email address.
                        </p>
                        <div style="text-align: center; margin: 32px 0;">
                            <a href="%s" style="background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 12px 28px; font-weight: 600; font-size: 15px; border-radius: 8px; display: inline-block; box-shadow: 0 2px 4px rgba(37,99,235,0.2);">
                                Verify Email Address
                            </a>
                        </div>
                        <p style="margin: 24px 0 8px 0; font-size: 13px; color: #64748b;">
                            Button not working? Copy and paste this URL into your browser:
                        </p>
                        <p style="margin: 0 0 24px 0; font-size: 12px; word-break: break-all; color: #2563eb;">
                            <a href="%s" style="color: #2563eb; text-decoration: underline;">%s</a>
                        </p>
                        <div style="background-color: #f1f5f9; border-left: 4px solid #3b82f6; padding: 12px 16px; border-radius: 4px; margin: 24px 0;">
                            <p style="margin: 0; font-size: 13px; color: #475569;">
                                ⏱ <strong>Expiration Notice:</strong> This link is valid for <strong>%d hours</strong> and can only be used once.
                            </p>
                        </div>
                        <p style="margin: 16px 0 0 0; font-size: 13px; color: #94a3b8;">
                            If you did not create a PayCore account, no action is needed. This email was sent to confirm ownership.
                        </p>
                        """, name, verificationUrl, verificationUrl, verificationUrl, expiryHours)
        );

        return new EmailContent(subject, htmlBody, textBody);
    }

    public EmailContent createPasswordResetEmail(String recipientName, String resetUrl, int expiryMinutes) {
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : "Valued Partner";
        String subject = "Password Reset Request — PayCore Security";

        String textBody = String.format("""
                Hello %s,

                We received a request to reset the password for your PayCore account.
                You can set a new password by navigating to the following link:

                %s

                This reset link will expire in %d minutes and can only be used once.
                If you did not request this password reset, please contact security immediately. Your current password remains unchanged.

                Sincerely,
                The PayCore Security Team
                """, name, resetUrl, expiryMinutes);

        String htmlBody = wrapHtmlTemplate(
                "Password Reset Request",
                String.format("""
                        <p style="margin: 0 0 16px 0; font-size: 15px; line-height: 1.6; color: #334155;">Hello <strong>%s</strong>,</p>
                        <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #334155;">
                            We received a request to reset the password for your <strong>PayCore</strong> account. Click the button below to choose a new password.
                        </p>
                        <div style="text-align: center; margin: 32px 0;">
                            <a href="%s" style="background-color: #0f172a; color: #ffffff; text-decoration: none; padding: 12px 28px; font-weight: 600; font-size: 15px; border-radius: 8px; display: inline-block; box-shadow: 0 2px 4px rgba(15,23,42,0.2);">
                                Reset Password
                            </a>
                        </div>
                        <p style="margin: 24px 0 8px 0; font-size: 13px; color: #64748b;">
                            Button not working? Copy and paste this URL into your browser:
                        </p>
                        <p style="margin: 0 0 24px 0; font-size: 12px; word-break: break-all; color: #2563eb;">
                            <a href="%s" style="color: #2563eb; text-decoration: underline;">%s</a>
                        </p>
                        <div style="background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 12px 16px; border-radius: 4px; margin: 24px 0;">
                            <p style="margin: 0; font-size: 13px; color: #991b1b;">
                                🔒 <strong>Security Notice:</strong> This link will expire in <strong>%d minutes</strong>. If you did not request a password reset, you can safely ignore this email; your existing password will remain unchanged.
                            </p>
                        </div>
                        """, name, resetUrl, resetUrl, resetUrl, expiryMinutes)
        );

        return new EmailContent(subject, htmlBody, textBody);
    }

    public EmailContent createPasswordChangedEmail(String recipientName) {
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : "Valued Partner";
        String subject = "Security Alert: Password Changed — PayCore";

        String textBody = String.format("""
                Hello %s,

                The password for your PayCore account was successfully changed.
                All previous sessions and refresh tokens have been revoked for your security.

                If you did not perform this action, please contact security immediately.

                Sincerely,
                The PayCore Security Team
                """, name);

        String htmlBody = wrapHtmlTemplate(
                "Password Successfully Updated",
                String.format("""
                        <p style="margin: 0 0 16px 0; font-size: 15px; line-height: 1.6; color: #334155;">Hello <strong>%s</strong>,</p>
                        <p style="margin: 0 0 20px 0; font-size: 15px; line-height: 1.6; color: #334155;">
                            This is an automated confirmation that your <strong>PayCore</strong> account password has been successfully changed.
                        </p>
                        <div style="background-color: #ecfdf5; border-left: 4px solid #10b981; padding: 12px 16px; border-radius: 4px; margin: 20px 0;">
                            <p style="margin: 0; font-size: 13px; color: #065f46;">
                                ✓ <strong>Security Update:</strong> All existing active sessions and refresh tokens have been invalidated.
                            </p>
                        </div>
                        <p style="margin: 16px 0 0 0; font-size: 13px; color: #64748b;">
                            If you initiated this change, no further action is necessary. If you did not authorize this change, please contact platform administrators immediately to secure your account.
                        </p>
                        """, name)
        );

        return new EmailContent(subject, htmlBody, textBody);
    }

    public EmailContent createAdminSetupEmail(String recipientName, String setupUrl, int expiryHours) {
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName.trim() : "Platform Administrator";
        String subject = "Initial Administrator Account Setup — PayCore";

        String textBody = String.format("""
                Hello %s,

                You have been designated as the Initial Platform Administrator for PayCore Payment Processing Engine.
                To activate your administrator credentials, please complete account configuration at:

                %s

                This invitation link will expire in %d hours.
                Ensure you configure a strong, unique master password.

                Sincerely,
                PayCore Security Provisioning
                """, name, setupUrl, expiryHours);

        String htmlBody = wrapHtmlTemplate(
                "Platform Administrator Setup",
                String.format("""
                        <p style="margin: 0 0 16px 0; font-size: 15px; line-height: 1.6; color: #334155;">Hello <strong>%s</strong>,</p>
                        <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #334155;">
                            Your email address has been provisioned as the <strong>Initial Platform Administrator</strong> for the PayCore Payment Processing Engine.
                        </p>
                        <div style="text-align: center; margin: 32px 0;">
                            <a href="%s" style="background-color: #1e3a8a; color: #ffffff; text-decoration: none; padding: 12px 28px; font-weight: 600; font-size: 15px; border-radius: 8px; display: inline-block; box-shadow: 0 2px 4px rgba(30,58,138,0.25);">
                                Complete Admin Setup
                            </a>
                        </div>
                        <p style="margin: 24px 0 8px 0; font-size: 13px; color: #64748b;">
                            Button not working? Copy and paste this URL into your browser:
                        </p>
                        <p style="margin: 0 0 24px 0; font-size: 12px; word-break: break-all; color: #2563eb;">
                            <a href="%s" style="color: #2563eb; text-decoration: underline;">%s</a>
                        </p>
                        <div style="background-color: #fffbeb; border-left: 4px solid #f59e0b; padding: 12px 16px; border-radius: 4px; margin: 24px 0;">
                            <p style="margin: 0; font-size: 13px; color: #92400e;">
                                ⚠️ <strong>Confidential Link:</strong> This setup token is valid for <strong>%d hours</strong> and grants elevated platform administrative privileges. Keep this link secure.
                            </p>
                        </div>
                        """, name, setupUrl, setupUrl, setupUrl, expiryHours)
        );

        return new EmailContent(subject, htmlBody, textBody);
    }

    private String wrapHtmlTemplate(String title, String contentHtml) {
        return String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b;">
                    <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="table-layout: fixed;">
                        <tr>
                            <td align="center" style="padding: 40px 16px;">
                                <table border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 580px; background-color: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);">
                                    <!-- Header -->
                                    <tr>
                                        <td style="background-color: #0f172a; padding: 24px 32px; text-align: left;">
                                            <table border="0" cellpadding="0" cellspacing="0" width="100%%">
                                                <tr>
                                                    <td>
                                                        <span style="color: #ffffff; font-size: 20px; font-weight: 700; letter-spacing: -0.5px;">PayCore</span>
                                                        <span style="color: #60a5fa; font-size: 12px; font-weight: 600; margin-left: 8px; text-transform: uppercase; letter-spacing: 1px;">Security</span>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <!-- Title -->
                                    <tr>
                                        <td style="padding: 32px 32px 12px 32px;">
                                            <h1 style="margin: 0; font-size: 22px; font-weight: 700; color: #0f172a; letter-spacing: -0.3px;">%s</h1>
                                        </td>
                                    </tr>
                                    <!-- Body -->
                                    <tr>
                                        <td style="padding: 0 32px 32px 32px;">
                                            %s
                                        </td>
                                    </tr>
                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px 32px; text-align: center;">
                                            <p style="margin: 0 0 6px 0; font-size: 12px; color: #94a3b8;">
                                                PayCore Payment Processing Engine &bull; Enterprise Financial Architecture
                                            </p>
                                            <p style="margin: 0; font-size: 11px; color: #cbd5e1;">
                                                This is an automated system notification. Replies to this email address are not monitored.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """, title, title, contentHtml);
    }
}
