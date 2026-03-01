package com.bugisiw.marketplace.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestTemplate interceptor that adds Authorization: Bearer &lt;service_token&gt; to outbound requests.
 */
@RequiredArgsConstructor
@Slf4j
public class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    private final ServiceTokenClient serviceTokenClient;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String token = serviceTokenClient.getAccessToken();
        String tokenPreview = token == null ? "null" : (token.length() <= 50 ? token : token.substring(0, 30) + "..." + token.substring(token.length() - 15));
        log.info(">>>>>STOKEN [interceptor] adding Bearer to outbound request method={} uri={} tokenPreview={}", request.getMethod(), request.getURI(), tokenPreview);
        request.getHeaders().setBearerAuth(token);
        ClientHttpResponse response = execution.execute(request, body);
        log.info(">>>>>STOKEN [interceptor] outbound completed method={} uri={} status={} tokenPreview={}", request.getMethod(), request.getURI(), response.getStatusCode(), tokenPreview);
        return response;
    }
}
