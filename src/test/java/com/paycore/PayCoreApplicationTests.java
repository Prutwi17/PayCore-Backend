package com.paycore;

import com.paycore.common.exception.ErrorResponse;
import com.paycore.common.exception.GlobalExceptionHandler;
import com.paycore.common.exception.ResourceNotFoundException;
import com.paycore.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class PayCoreApplicationTests {

    @Test
    @DisplayName("Application context should bootstrap successfully with test configuration")
    void contextLoads() {
        // Verifies Spring context initializes without bean collision or configuration errors
    }

    @Test
    @DisplayName("ApiResponse envelope should serialize success flag and timestamp")
    void testApiResponseEnvelope() {
        ApiResponse<String> response = ApiResponse.success("Operation completed", "test-payload");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Operation completed");
        assertThat(response.getData()).isEqualTo("test-payload");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("GlobalExceptionHandler should translate ResourceNotFoundException to standard 404 ErrorResponse")
    void testGlobalExceptionHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResourceNotFoundException ex = new ResourceNotFoundException("Payment", "id", "PAY_0001");

        ResponseEntity<ErrorResponse> entity = handler.handleBaseException(ex);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().isSuccess()).isFalse();
        assertThat(entity.getBody().getError().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(entity.getBody().getError().getMessage()).contains("Payment not found with id: 'PAY_0001'");
    }
}
