package com.paycore.webhook.security;

import com.paycore.webhook.exception.WebhookSsrfException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Validates outgoing webhook destination URLs against Server-Side Request Forgery (SSRF)
 * and DNS rebinding attacks.
 */
@Component
public class WebhookSsrfValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSsrfValidator.class);
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Value("${paycore.webhook.allow-local-loopback:false}")
    private boolean allowLocalLoopback;

    public void validateUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new WebhookSsrfException("Webhook destination URL cannot be empty");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (IllegalArgumentException e) {
            throw new WebhookSsrfException("Invalid webhook URL syntax: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new WebhookSsrfException("Unsupported URL scheme: " + scheme + ". Only HTTP and HTTPS are permitted.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new WebhookSsrfException("Webhook URL must contain a valid destination host");
        }

        // Test environment bypass if explicitly configured
        if (allowLocalLoopback && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"))) {
            return;
        }

        // Reject explicit loopback names
        if (host.equalsIgnoreCase("localhost") || host.equalsIgnoreCase("localhost.localdomain") || host.endsWith(".localhost")) {
            throw new WebhookSsrfException("Destination host cannot be localhost or loopback");
        }

        // Resolve DNS and validate each returned IP to mitigate DNS rebinding
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                validateAddress(address);
            }
        } catch (UnknownHostException e) {
            log.warn("DNS resolution failed for webhook host: {}", host);
            throw new WebhookSsrfException("Unable to resolve destination host: " + host);
        }
    }

    private void validateAddress(InetAddress address) {
        if (address.isLoopbackAddress()) {
            throw new WebhookSsrfException("Destination resolves to a loopback address: " + address.getHostAddress());
        }
        if (address.isAnyLocalAddress()) {
            throw new WebhookSsrfException("Destination resolves to a wildcard/any-local address: " + address.getHostAddress());
        }
        if (address.isSiteLocalAddress()) {
            throw new WebhookSsrfException("Destination resolves to a private RFC-1918 address: " + address.getHostAddress());
        }
        if (address.isLinkLocalAddress()) {
            throw new WebhookSsrfException("Destination resolves to a link-local address: " + address.getHostAddress());
        }
        if (address.isMulticastAddress()) {
            throw new WebhookSsrfException("Destination resolves to a multicast address: " + address.getHostAddress());
        }

        // Additional private IP range check (e.g. 100.64.0.0/10 Carrier-grade NAT)
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
                throw new WebhookSsrfException("Destination resolves to Carrier-Grade NAT private address");
            }
        }
    }

    public void setAllowLocalLoopback(boolean allowLocalLoopback) {
        this.allowLocalLoopback = allowLocalLoopback;
    }
}
