package vn.vnuis.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** The /api/v1/ai/** proxy: access rules, header forwarding, status pass-through. */
class AiProxyIT extends AbstractIntegrationTest {

    @Test
    void forecastAndMetricsArePublicAndQueryIsForwarded() {
        ResponseEntity<String> r = call(HttpMethod.GET, "/ai/forecast?zoneCode=A&hours=24", null, null);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        JsonNode echo = json(r);
        assertThat(echo.get("path").asText()).isEqualTo("/api/v1/ai/forecast");
        assertThat(echo.get("query").asText()).isEqualTo("zoneCode=A&hours=24");
        assertThat(call(HttpMethod.GET, "/ai/metrics", null, null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void chatNeedsATokenAndForwardsIt() {
        assertThat(call(HttpMethod.POST, "/ai/chat", null, Map.of("message", "xin chao")).getStatusCode().value())
                .isEqualTo(401);
        ResponseEntity<String> r = call(HttpMethod.POST, "/ai/chat", tokenFor("SV001"), Map.of("message", "xin chao"));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        JsonNode echo = json(r);
        assertThat(echo.get("method").asText()).isEqualTo("POST");
        assertThat(echo.get("authForwarded").asBoolean()).isTrue();
        assertThat(echo.get("bodyLength").asInt()).isPositive();
    }

    @Test
    void upstreamErrorStatusIsPassedThrough() {
        ResponseEntity<String> r = call(HttpMethod.POST, "/ai/chat", tokenFor("SV001"), Map.of("message", "please-conflict"));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void adminEndpointsNeedTheAdminRole() {
        assertThat(call(HttpMethod.POST, "/ai/admin/train", tokenFor("SV001"), null).getStatusCode().value())
                .isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/ai/admin/train", tokenFor("ADMIN001"), null).getStatusCode().value())
                .isEqualTo(200);
    }
}
