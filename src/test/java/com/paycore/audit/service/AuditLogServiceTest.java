package com.paycore.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.audit.dto.AuditLogResponseDto;
import com.paycore.audit.entity.AuditLog;
import com.paycore.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Audit Log Service Unit Tests")
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditDataSanitizer dataSanitizer;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        dataSanitizer = new AuditDataSanitizer(new ObjectMapper());
        auditLogService = new AuditLogService(auditLogRepository, dataSanitizer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should sanitize sensitive keys (passwords, tokens, CVV, card numbers) before saving audit log")
    void testSensitiveDataSanitization() {
        Map<String, Object> beforeState = Map.of(
                "username", "john_merchant",
                "password", "SuperSecret123!",
                "apiKey", "pk_live_abcdef123456",
                "cvv", "123"
        );

        Map<String, Object> afterState = Map.of(
                "username", "john_merchant",
                "token", "jwt.token.here",
                "status", "ACTIVE"
        );

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        AuditLog saved = auditLogService.logAction(
                "admin1", "ROLE_ADMIN", 100L, "MERCHANT_UPDATED",
                "MERCHANT", "100", "Updated merchant credentials",
                beforeState, afterState, "Security policy update"
        );

        assertNotNull(saved);
        assertFalse(saved.getBeforeData().contains("SuperSecret123!"));
        assertFalse(saved.getBeforeData().contains("pk_live_abcdef123456"));
        assertFalse(saved.getBeforeData().contains("123"));
        assertTrue(saved.getBeforeData().contains("[REDACTED]"));

        assertFalse(saved.getAfterData().contains("jwt.token.here"));
        assertTrue(saved.getAfterData().contains("[REDACTED]"));
        assertTrue(saved.getAfterData().contains("ACTIVE"));

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should automatically resolve actor details from SecurityContextHolder")
    void testSecurityContextActorResolution() {
        SecurityContext securityContext = mock(SecurityContext.class);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "system_admin@paycore.com",
                "credentials",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        auditLogService.logAction(
                "PAYMENT_REFUNDED", "PAYMENT", "pay_xyz",
                "Manual refund executed", null, Map.of("amount", 50), "Customer dispute"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog captured = captor.getValue();
        assertEquals("system_admin@paycore.com", captured.getActorId());
        assertEquals("ROLE_ADMIN", captured.getActorRole());
        assertEquals("PAYMENT_REFUNDED", captured.getAction());
    }

    @Test
    @DisplayName("Should retrieve paginated audit logs with filters")
    void testGetAuditLogsPagination() {
        AuditLog logEntry = new AuditLog(
                "admin1", "ROLE_ADMIN", 1L, "STATUS_CHANGE",
                "PAYMENT", "pay_01", "Updated payment status",
                null, null, "Investigation", null, null
        );

        Page<AuditLog> page = new PageImpl<>(List.of(logEntry));
        when(auditLogRepository.findFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<AuditLogResponseDto> result = auditLogService.getAuditLogs(
                "admin1", "STATUS_CHANGE", "PAYMENT", "pay_01", 1L,
                Instant.now().minusSeconds(3600), Instant.now(), PageRequest.of(0, 10)
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("STATUS_CHANGE", result.getContent().get(0).getAction());
        assertEquals("admin1", result.getContent().get(0).getActorId());
    }
}
