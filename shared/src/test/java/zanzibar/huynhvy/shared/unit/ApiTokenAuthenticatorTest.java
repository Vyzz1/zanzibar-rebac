package zanzibar.huynhvy.shared.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.shared.auth.ApiClient;
import zanzibar.huynhvy.shared.auth.ApiScope;
import zanzibar.huynhvy.shared.auth.ApiTokenAuthenticator;
import zanzibar.huynhvy.shared.auth.AuthProperties;
import zanzibar.huynhvy.shared.auth.GrpcMethodScopes;

class ApiTokenAuthenticatorTest {

  private static final ApiClient READER =
      new ApiClient("analytics", "reader-token", Set.of(ApiScope.READ));
  private static final ApiClient WRITER =
      new ApiClient("app", "writer-token", Set.of(ApiScope.READ, ApiScope.WRITE));

  private final ApiTokenAuthenticator authenticator =
      new ApiTokenAuthenticator(new AuthProperties(true, List.of(READER, WRITER), ""));

  @Test
  void resolves_a_known_bearer_token_to_its_client() {
    assertThat(authenticator.authenticate("Bearer writer-token")).contains(WRITER);
  }

  @Test
  void rejects_a_missing_malformed_or_unknown_token() {
    assertThat(authenticator.authenticate(null)).isEmpty();
    assertThat(authenticator.authenticate("")).isEmpty();
    assertThat(authenticator.authenticate("reader-token")).isEmpty(); // no Bearer prefix
    assertThat(authenticator.authenticate("Bearer nope")).isEmpty();
    assertThat(authenticator.authenticate("Bearer reader-token-extra")).isEmpty();
  }

  @Test
  void a_disabled_authenticator_reports_itself_as_off() {
    ApiTokenAuthenticator disabled =
        new ApiTokenAuthenticator(new AuthProperties(false, List.of(), ""));

    assertThat(disabled.isEnabled()).isFalse();
    assertThat(authenticator.isEnabled()).isTrue();
  }

  @Test
  void scopes_are_not_hierarchical() {
    assertThat(READER.hasScope(ApiScope.READ)).isTrue();
    assertThat(READER.hasScope(ApiScope.WRITE)).isFalse();
    assertThat(WRITER.hasScope(ApiScope.ADMIN)).isFalse();
  }

  @Test
  void reads_need_read_and_tuple_changes_need_write() {
    assertThat(GrpcMethodScopes.required("zanzibar.api.v1.AuthorizationService/Check"))
        .isEqualTo(ApiScope.READ);
    assertThat(GrpcMethodScopes.required("zanzibar.api.v1.NamespaceService/GetNamespaceConfig"))
        .isEqualTo(ApiScope.READ);
    assertThat(GrpcMethodScopes.required("zanzibar.api.v1.AuthorizationService/WriteTuples"))
        .isEqualTo(ApiScope.WRITE);
    assertThat(GrpcMethodScopes.required("zanzibar.api.v1.AuthorizationService/DeleteTuples"))
        .isEqualTo(ApiScope.WRITE);
  }

  @Test
  void an_unclassified_method_falls_closed_to_admin() {
    assertThat(GrpcMethodScopes.required("zanzibar.api.v1.AuthorizationService/SomethingNew"))
        .isEqualTo(ApiScope.ADMIN);
  }

  @Test
  void the_health_service_stays_open_for_probes() {
    assertThat(GrpcMethodScopes.isOpen("grpc.health.v1.Health/Check")).isTrue();
    assertThat(GrpcMethodScopes.isOpen("zanzibar.api.v1.AuthorizationService/Check")).isFalse();
  }
}
