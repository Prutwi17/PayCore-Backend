package com.paycore.email;

import com.paycore.email.dto.EmailStatusDto;

public interface EmailService {

    void sendVerificationEmail(String toEmail, String recipientName, String rawToken);

    void sendPasswordResetEmail(String toEmail, String recipientName, String rawToken);

    void sendPasswordChangedNotification(String toEmail, String recipientName);

    void sendAdminSetupEmail(String toEmail, String recipientName, String rawToken);

    boolean isConfigured();

    EmailStatusDto getStatus();
}
