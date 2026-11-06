package com.paycore.webhook.security;

import com.paycore.webhook.exception.WebhookSsrfException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Webhook SSRF Validator Unit Tests")
class WebhookSsrfValidatorTest {

    private WebhookSsrfValidator ssrfValidator;

    @BeforeEach
    void setUp() {
        ssrfValidator = new WebhookSsrfValidator();
        ssrfValidator.setAllowLocalLoopback(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080/hook",
            "http://127.0.0.1:9000/webhook",
            "http://127.0.1.1/notify",
            "http://[::1]/webhook",
            "http://sub.localhost/webhook"
    })
    @DisplayName("Loopback destinations are strictly rejected")
    void testLoopbackRejected(String url) {
        assertThrows(WebhookSsrfException.class, () -> ssrfValidator.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://10.0.0.1/hook",
            "http://172.16.0.10/webhook",
            "http://192.168.1.1/notify",
            "http://169.254.169.254/latest/meta-data"
    })
    @DisplayName("Private RFC-1918 and AWS link-local IP addresses are rejected")
    void testPrivateIpsRejected(String url) {
        assertThrows(WebhookSsrfException.class, () -> ssrfValidator.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/webhook",
            "file:///etc/passwd",
            "gopher://example.com/",
            "javascript:alert(1)"
    })
    @DisplayName("Unsupported URL schemes are rejected")
    void testUnsupportedSchemesRejected(String url) {
        assertThrows(WebhookSsrfException.class, () -> ssrfValidator.validateUrl(url));
    }

    @Test
    @DisplayName("Empty or null URLs are rejected")
    void testEmptyUrlRejected() {
        assertThrows(WebhookSsrfException.class, () -> ssrfValidator.validateUrl(null));
        assertThrows(WebhookSsrfException.class, () -> ssrfValidator.validateUrl("   "));
    }

    @Test
    @DisplayName("Allow local loopback flag permits localhost in development/testing when enabled")
    void testLocalLoopbackPermittedWhenFlagTrue() {
        ssrfValidator.setAllowLocalLoopback(true);
        assertDoesNotThrow(() -> ssrfValidator.validateUrl("http://localhost:8080/test-hook"));
    }
}
