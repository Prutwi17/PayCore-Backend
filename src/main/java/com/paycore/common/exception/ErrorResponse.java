package com.paycore.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Standard Error Response envelope for PayCore.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private boolean success = false;
    private ErrorDetail error;
    private Instant timestamp;

    public ErrorResponse() {
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String code, String message) {
        this.error = new ErrorDetail(code, message, null);
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String code, String message, List<ValidationErrorDetail> details) {
        this.error = new ErrorDetail(code, message, details);
        this.timestamp = Instant.now();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private String code;
        private String message;
        private List<ValidationErrorDetail> details;

        public ErrorDetail() {
        }

        public ErrorDetail(String code, String message, List<ValidationErrorDetail> details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<ValidationErrorDetail> getDetails() {
            return details;
        }

        public void setDetails(List<ValidationErrorDetail> details) {
            this.details = details;
        }
    }

    public static class ValidationErrorDetail {
        private String field;
        private String issue;

        public ValidationErrorDetail() {
        }

        public ValidationErrorDetail(String field, String issue) {
            this.field = field;
            this.issue = issue;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getIssue() {
            return issue;
        }

        public void setIssue(String issue) {
            this.issue = issue;
        }
    }
}
