package vn.vnuis.library.face;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.vnuis.library.error.ApiException;

/** Calls face-service {@code POST /v1/analyze} (internal network, shared X-Internal-Key). */
@Component
public class FaceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FaceServiceClient.class);

    public record Frame(String step, String image) {
    }

    record AnalyzeRequest(List<String> steps, List<Frame> frames) {
    }

    /** {@code status}: OK, NO_FACE, MULTIPLE_FACES or LIVENESS_FAILED; embedding only when OK. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnalyzeResult(String status, String reason, float[] embedding, Double consistency, String model,
                                Integer latencyMs) {

        public boolean ok() {
            return "OK".equals(status) && embedding != null && embedding.length > 0;
        }
    }

    private final RestClient client;

    public FaceServiceClient(FaceProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds()));
        this.client = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader("X-Internal-Key", props.internalKey())
                .build();
    }

    public AnalyzeResult analyze(List<String> steps, List<Frame> frames) {
        try {
            AnalyzeResult result = client.post()
                    .uri("/v1/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AnalyzeRequest(steps, frames))
                    .retrieve()
                    .body(AnalyzeResult.class);
            if (result == null) {
                throw unavailable();
            }
            return result;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 413
                    || e.getStatusCode().value() == 422) {
                throw ApiException.badRequest("INVALID_IMAGE", "Camera frames could not be read");
            }
            log.warn("face-service rejected the call: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw unavailable();
        } catch (RestClientException e) {
            log.warn("face-service unreachable: {}", e.getMessage());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FACE_SERVICE_UNAVAILABLE",
                "Face verification is temporarily unavailable");
    }
}
