package com.paycore.payment.provider;

public class PaymentProviderResponse {

    private PaymentProviderStatus status;
    private String providerTransactionId;
    private String responseCode;
    private String responseMessage;
    private String actionUrl;

    public PaymentProviderResponse() {
    }

    public PaymentProviderResponse(PaymentProviderStatus status, String providerTransactionId,
                                   String responseCode, String responseMessage, String actionUrl) {
        this.status = status;
        this.providerTransactionId = providerTransactionId;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.actionUrl = actionUrl;
    }

    public static PaymentProviderResponse success(String providerTransactionId, String responseCode, String message) {
        return new PaymentProviderResponse(PaymentProviderStatus.SUCCESS, providerTransactionId, responseCode, message, null);
    }

    public static PaymentProviderResponse failed(String providerTransactionId, String responseCode, String message) {
        return new PaymentProviderResponse(PaymentProviderStatus.FAILED, providerTransactionId, responseCode, message, null);
    }

    public static PaymentProviderResponse requiresAction(String providerTransactionId, String actionUrl, String message) {
        return new PaymentProviderResponse(PaymentProviderStatus.REQUIRES_ACTION, providerTransactionId, "ACTION_REQUIRED_3DS", message, actionUrl);
    }

    public static PaymentProviderResponse unknown(String providerTransactionId, String responseCode, String message) {
        return new PaymentProviderResponse(PaymentProviderStatus.UNKNOWN, providerTransactionId, responseCode, message, null);
    }

    public static PaymentProviderResponse cancelled(String providerTransactionId, String message) {
        return new PaymentProviderResponse(PaymentProviderStatus.CANCELLED, providerTransactionId, "CANCELLED_BY_MERCHANT", message, null);
    }

    public PaymentProviderStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentProviderStatus status) {
        this.status = status;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }
}
