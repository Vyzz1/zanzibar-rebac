package zanzibar.huynhvy.namespace.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * End-to-end REST test against a real Postgres (via {@link BaseIntegrationTest}). namespace-manager
 * owns its Flyway migration, so the container gets {@code namespace_configs} from {@code V1}.
 *
 * <p>Bodies are sent as raw JSON strings — the exact wire format a client sends — so the test
 * exercises the real polymorphic {@code Map<String, UsersetRewrite>} deserialization path, not a
 * Java-side serialization that erasure could quietly break.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "auth.clients[0].name=admin",
      "auth.clients[0].token=admin-token",
      "auth.clients[0].scopes=read,write,admin",
      "auth.clients[1].name=reader",
      "auth.clients[1].token=reader-token",
      "auth.clients[1].scopes=read"
    })
class NamespaceControllerIntegrationTest extends BaseIntegrationTest {

  // viewer = union(this, computedUserset(editor)); editor = this. All refs defined, no cycle.
  private static final String VALID_CONFIG =
      """
      {
        "editor": { "this": {} },
        "viewer": { "union": { "children": [
          { "this": {} },
          { "computedUserset": { "relation": "editor" } }
        ] } }
      }
      """;

  // viewer -> editor -> viewer via ComputedUserset: a config-time cycle.
  private static final String CYCLIC_CONFIG =
      """
      {
        "viewer": { "computedUserset": { "relation": "editor" } },
        "editor": { "computedUserset": { "relation": "viewer" } }
      }
      """;

  // viewer references "owner", which is not defined in this namespace.
  private static final String UNDEFINED_REF_CONFIG =
      """
      { "viewer": { "computedUserset": { "relation": "owner" } } }
      """;

  private static final String ADMIN_TOKEN = "admin-token";
  private static final String READER_TOKEN = "reader-token";

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private TestRestTemplate rest;

  @Test
  void put_then_get_returns_the_stored_config_at_version_1() throws Exception {
    ResponseEntity<String> put = put("doc-basic", VALID_CONFIG);
    assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(versionOf(put)).isEqualTo(1);

    ResponseEntity<String> get = get("/api/v1/namespaces/doc-basic", String.class);
    assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = mapper.readTree(get.getBody());
    assertThat(body.get("namespace").asText()).isEqualTo("doc-basic");
    assertThat(body.get("version").asInt()).isEqualTo(1);
    // The polymorphic tag survives the round-trip through JSONB.
    assertThat(body.at("/relations/viewer/union/children/1/computedUserset/relation").asText())
        .isEqualTo("editor");
  }

  @Test
  void a_second_put_appends_the_next_version_and_get_returns_the_latest() throws Exception {
    assertThat(versionOf(put("doc-versioned", VALID_CONFIG))).isEqualTo(1);
    assertThat(versionOf(put("doc-versioned", VALID_CONFIG))).isEqualTo(2);

    ResponseEntity<String> latest = get("/api/v1/namespaces/doc-versioned", String.class);
    assertThat(mapper.readTree(latest.getBody()).get("version").asInt()).isEqualTo(2);

    // The older version is still retrievable — writes are append-only.
    ResponseEntity<String> v1 = get("/api/v1/namespaces/doc-versioned/versions/1", String.class);
    assertThat(v1.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(mapper.readTree(v1.getBody()).get("version").asInt()).isEqualTo(1);
  }

  @Test
  void put_with_a_cyclic_config_is_rejected_with_400() {
    assertThat(put("doc-cyclic", CYCLIC_CONFIG).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void put_referencing_an_undefined_relation_is_rejected_with_400() {
    assertThat(put("doc-undefined", UNDEFINED_REF_CONFIG).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void get_of_an_unknown_namespace_returns_404() {
    assertThat(get("/api/v1/namespaces/does-not-exist", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void a_request_without_a_token_is_unauthorized() {
    assertThat(exchange(HttpMethod.GET, "/api/v1/namespaces/doc-basic", null, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void a_read_only_token_may_read_but_not_change_a_namespace() {
    put("doc-scoped", VALID_CONFIG); // seeded by the admin token

    assertThat(
            exchange(HttpMethod.GET, "/api/v1/namespaces/doc-scoped", null, READER_TOKEN)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // Editing a namespace config rewrites how every relation is derived, so it needs admin.
    assertThat(
            exchange(HttpMethod.PUT, "/api/v1/namespaces/doc-scoped", VALID_CONFIG, READER_TOKEN)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  private ResponseEntity<String> put(String namespace, String body) {
    return exchange(HttpMethod.PUT, "/api/v1/namespaces/" + namespace, body, ADMIN_TOKEN);
  }

  private ResponseEntity<String> get(String path, Class<String> type) {
    return exchange(HttpMethod.GET, path, null, ADMIN_TOKEN);
  }

  private ResponseEntity<String> exchange(
      HttpMethod method, String path, String body, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
  }

  private int versionOf(ResponseEntity<String> response) throws Exception {
    return mapper.readTree(response.getBody()).get("version").asInt();
  }
}
