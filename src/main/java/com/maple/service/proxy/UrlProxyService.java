package com.maple.service.proxy;

import com.maple.dto.UrlProxyRequestDto;
import com.maple.dto.UrlProxyResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to proxy HTTP GET/POST requests to user-provided URLs.
 *
 * <p>Implements SSRF protection:
 * <ul>
 *   <li>Only HTTP/HTTPS protocols allowed
 *   <li>Blocks private/reserved IP ranges (localhost, 10.x, 172.16-31.x, 192.168.x, 169.254.x)
 *   <li>Blocks cloud metadata endpoints
 *   <li>DNS rebinding mitigation via resolved-IP validation
 *   <li>Request and response timeouts
 * </ul>
 */
@Service
public class UrlProxyService {

    private static final Logger logger = LoggerFactory.getLogger(UrlProxyService.class);

    private static final int MAX_RESPONSE_BODY_BYTES = 1_048_576; // 1 MiB
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of(
                    "authorization",
                    "cookie",
                    "set-cookie",
                    "x-api-key",
                    "proxy-authorization");

    private final RestTemplate restTemplate;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public UrlProxyService(
            RestTemplate urlProxyRestTemplate,
            @Value("${maple.url-proxy.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${maple.url-proxy.read-timeout-ms:10000}") int readTimeoutMs) {
        this.restTemplate = urlProxyRestTemplate;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Execute a proxied GET or POST request to the given URL.
     *
     * @param request the proxy request (url, method, optional body)
     * @return the target server's response
     * @throws UrlProxyException if URL is invalid, blocked, or request fails
     */
    public UrlProxyResponseDto proxy(UrlProxyRequestDto request) {
        String url = request.getUrl().trim();
        validateUrl(url);

        logger.info("UrlProxy request: method={} url={}", request.getMethod(), sanitizeForLog(url));

        try {
            URI uri = URI.create(url);

            // Resolve and validate host - block private/metadata IPs
            validateResolvedHost(uri.getHost());

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));

            HttpEntity<?> entity;
            if (request.getMethod() == UrlProxyRequestDto.HttpMethod.POST && request.getBody() != null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
                entity = new HttpEntity<>(request.getBody(), headers);
            } else {
                entity = new HttpEntity<>(headers);
            }

            RequestEntity<?> requestEntity;
            if (request.getMethod() == UrlProxyRequestDto.HttpMethod.POST) {
                requestEntity =
                        RequestEntity.post(uri)
                                .headers(headers)
                                .body(entity.getBody() != null ? entity.getBody() : "");
            } else {
                requestEntity = RequestEntity.get(uri).headers(headers).build();
            }

            ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
            String body = response.getBody();
            if (body != null && body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BODY_BYTES) {
                body = body.substring(0, MAX_RESPONSE_BODY_BYTES / 4) + "\n... [truncated]";
            }

            Map<String, String> filteredHeaders = filterHeaders(response.getHeaders());

            return UrlProxyResponseDto.builder()
                    .statusCode(response.getStatusCode().value())
                    .headers(filteredHeaders)
                    .body(body)
                    .build();

        } catch (ResourceAccessException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            if (msg != null && msg.contains("timed out")) {
                throw new UrlProxyException("Request to target URL timed out", e);
            }
            throw new UrlProxyException("Failed to connect to target URL", e);
        } catch (Exception e) {
            if (e instanceof UrlProxyException) {
                throw (UrlProxyException) e;
            }
            throw new UrlProxyException("Proxy request failed: " + e.getMessage(), e);
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new UrlProxyException("URL is required");
        }
        if (url.length() > 2048) {
            throw new UrlProxyException("URL exceeds maximum length");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new UrlProxyException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new UrlProxyException("Only HTTP and HTTPS protocols are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UrlProxyException("URL must have a valid host");
        }

        // Block common internal hostnames
        String lower = host.toLowerCase();
        if (lower.equals("localhost")
                || lower.startsWith("localhost.")
                || lower.equals("metadata")
                || lower.equals("metadata.google.internal")
                || lower.endsWith(".internal")
                || lower.endsWith(".local")) {
            throw new UrlProxyException("Target host is not allowed");
        }
    }

    private void validateResolvedHost(String host) {
        if (host == null) return;

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isBlockedAddress(addr)) {
                    throw new UrlProxyException("Target host resolves to a blocked address");
                }
            }
        } catch (UnknownHostException e) {
            throw new UrlProxyException("Could not resolve target host");
        }
    }

    private boolean isBlockedAddress(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            // IPv4
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            // 127.0.0.0/8
            if (b0 == 127) return true;
            // 10.0.0.0/8
            if (b0 == 10) return true;
            // 172.16.0.0/12
            if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) return true;
            // 169.254.0.0/16 (link-local, metadata)
            if (b0 == 169 && b1 == 254) return true;
            // 0.0.0.0/8
            if (b0 == 0) return true;
        } else if (b.length == 16) {
            // IPv6 - block loopback, link-local, unique local
            if (b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 0 && b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0
                    && b[8] == 0 && b[9] == 0 && b[10] == 0 && b[11] == 0 && b[12] == 0 && b[13] == 0 && b[14] == 0
                    && b[15] == 1) {
                return true; // ::1
            }
            if ((b[0] & 0xFF) == 0xfe && (b[1] & 0xC0) == 0x80) return true; // fe80::/10 link-local
            if ((b[0] & 0xFF) == 0xfc || (b[0] & 0xFF) == 0xfd) return true; // fc00::/7 unique local
        }
        return false;
    }

    private Map<String, String> filterHeaders(HttpHeaders headers) {
        if (headers == null) return Map.of();
        return headers.entrySet().stream()
                .filter(e -> !SENSITIVE_HEADERS.contains(e.getKey().toLowerCase()))
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(", ", e.getValue())));
    }

    private String sanitizeForLog(String url) {
        if (url == null || url.length() < 80) return url;
        return url.substring(0, 60) + "...[truncated]";
    }

    /** Thrown when URL proxy validation fails or the proxied request fails. */
    public static class UrlProxyException extends RuntimeException {
        public UrlProxyException(String message) {
            super(message);
        }

        public UrlProxyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
