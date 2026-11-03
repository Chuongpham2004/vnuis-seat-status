package vn.vnuis.library.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import vn.vnuis.library.config.AppProperties;
import vn.vnuis.library.error.ApiException;

/**
 * Transparent proxy {@code /api/v1/ai/**} -> ai-service (same paths), so the web app only
 * talks to backend-core. The caller's {@code Authorization} header is forwarded unchanged:
 * ai-service uses it to call {@code /api/v1/ai-tools/*} back on behalf of the same student.
 * Access rules are in SecurityConfig (forecast/metrics public, chat authenticated, admin ADMIN).
 */
@RestController
public class AiProxyController {

    private static final Logger log = LoggerFactory.getLogger(AiProxyController.class);

    private final RestClient client;

    public AiProxyController(AppProperties.Ai props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds())); // training/LLM calls can be slow
        this.client = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
    }

    @RequestMapping("/api/v1/ai/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        String contentType = request.getContentType();
        try {
            return client.method(HttpMethod.valueOf(request.getMethod()))
                    .uri(query == null ? path : path + "?" + query)
                    .headers(h -> {
                        if (auth != null) {
                            h.set(HttpHeaders.AUTHORIZATION, auth);
                        }
                        if (contentType != null) {
                            h.set(HttpHeaders.CONTENT_TYPE, contentType);
                        }
                        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    })
                    .body(body)
                    .exchange((req, res) -> {
                        byte[] payload = StreamUtils.copyToByteArray(res.getBody());
                        HttpHeaders out = new HttpHeaders();
                        MediaType type = res.getHeaders().getContentType();
                        out.setContentType(type == null ? MediaType.APPLICATION_JSON : type);
                        return new ResponseEntity<>(payload, out, res.getStatusCode());
                    });
        } catch (ResourceAccessException e) {
            log.warn("ai-service unreachable for {} {}: {}", request.getMethod(), path, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE",
                    "The AI service is not reachable right now");
        }
    }
}
