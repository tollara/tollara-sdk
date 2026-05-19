package com.tollara.client;

/**
 * Thrown when an HTTP call to Tollara services fails or returns an unexpected status.
 */
public class TollaraHttpException extends RuntimeException {

    private final int statusCode;

    public TollaraHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public TollaraHttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
