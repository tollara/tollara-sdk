package com.agentvend.client;

/**
 * Thrown when an HTTP call to AgentVend services fails or returns an unexpected status.
 */
public class AgentVendHttpException extends RuntimeException {

    private final int statusCode;

    public AgentVendHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AgentVendHttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
