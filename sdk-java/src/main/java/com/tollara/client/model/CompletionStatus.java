package com.tollara.client.model;

/**
 * Completion status sent to the usage service (see docs/sdk-api-spec.md §3.3).
 */
public enum CompletionStatus {
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    private final String apiValue;

    CompletionStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    /** JSON body value for the {@code status} field. */
    public String getApiValue() {
        return apiValue;
    }
}
