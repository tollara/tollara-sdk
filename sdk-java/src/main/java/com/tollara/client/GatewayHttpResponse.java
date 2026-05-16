package com.tollara.client;

import lombok.Value;

/** Result of a gateway GET: HTTP status code and response body as text. */
@Value
public class GatewayHttpResponse {
    int statusCode;
    String body;

    public boolean is2xxSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
