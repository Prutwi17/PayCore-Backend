package com.paycore.audit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Strips and redacts sensitive credentials, secrets, raw payment card details,
 * and PII from before/after payloads before persistence in the immutable audit log.
 */
@Component
public class AuditDataSanitizer {

    private static final Logger log = LoggerFactory.getLogger(AuditDataSanitizer.class);
    private static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwordhash", "secret", "secretencrypted", "secretkey",
            "apikey", "key", "token", "accesstoken", "refreshtoken", "jwt",
            "cardnumber", "pan", "cvv", "cvc", "securitycode", "rawsecret",
            "webhooksecret", "privatekey", "clientsecret"
    );

    private final ObjectMapper objectMapper;

    public AuditDataSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Sanitizes an arbitrary object by serializing and masking known sensitive keys.
     */
    public String sanitize(Object data) {
        if (data == null) {
            return null;
        }

        if (data instanceof String str) {
            if (str.isBlank()) {
                return null;
            }
            try {
                // Attempt to parse as JSON map or list
                Object parsed = objectMapper.readValue(str, Object.class);
                Object sanitized = sanitizeRecursive(parsed);
                return objectMapper.writeValueAsString(sanitized);
            } catch (Exception e) {
                // Not standard JSON, perform regex mask on string
                return maskRegex(str);
            }
        }

        try {
            Map<String, Object> map = objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
            Object sanitized = sanitizeRecursive(map);
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception e) {
            log.debug("Unable to convert object to map for sanitization: {}", e.getMessage());
            return REDACTED;
        }
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeRecursive(Object obj) {
        if (obj instanceof Map<?, ?> rawMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String lowerKey = key.toLowerCase().replace("_", "").replace("-", "");
                if (SENSITIVE_KEYS.contains(lowerKey)) {
                    copy.put(key, REDACTED);
                } else {
                    copy.put(key, sanitizeRecursive(entry.getValue()));
                }
            }
            return copy;
        } else if (obj instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(sanitizeRecursive(item));
            }
            return copy;
        }
        return obj;
    }

    private String maskRegex(String str) {
        return str.replaceAll("(?i)(password|secret|apikey|token|cvv)\\s*[:=]\\s*['\"]?[^\\s,'\"]+", "$1=" + REDACTED);
    }
}
