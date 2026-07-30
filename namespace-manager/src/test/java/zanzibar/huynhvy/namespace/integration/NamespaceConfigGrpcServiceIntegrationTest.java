package zanzibar.huynhvy.namespace.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import zanzibar.huynhvy.api.GetNamespaceConfigRequest;
import zanzibar.huynhvy.api.GetNamespaceConfigResponse;
import zanzibar.huynhvy.api.NamespaceServiceGrpc;
import zanzibar.huynhvy.namespace.domain.NamespaceConfig;
import zanzibar.huynhvy.namespace.repository.NamespaceConfigRepository;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * Exercises the gRPC NamespaceService against a real Postgres. The server runs in-process (no port
 * binding) so the test drives it through an {@link InProcessChannelBuilder} channel, verifying both
 * the wiring and the not-found status mapping.
 */
@SpringBootTest
@TestPropertySource(properties = "grpc.server.in-process-name=nsm-test")
class NamespaceConfigGrpcServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private NamespaceConfigRepository repository;

  private ManagedChannel channel;
  private NamespaceServiceGrpc.NamespaceServiceBlockingStub stub;

  @BeforeEach
  void setUp() {
    channel = InProcessChannelBuilder.forName("nsm-test").directExecutor().build();
    stub = NamespaceServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void tearDown() {
    channel.shutdownNow();
  }

  @Test
  void serves_the_latest_config_json_and_version() {
    repository.save(NamespaceConfig.create("doc-grpc", 1, "{\"viewer\":{\"this\":{}}}"));

    GetNamespaceConfigResponse response =
        stub.getNamespaceConfig(
            GetNamespaceConfigRequest.newBuilder().setNamespace("doc-grpc").build());

    assertThat(response.getNamespace()).isEqualTo("doc-grpc");
    assertThat(response.getVersion()).isEqualTo(1);
    // Served as-is; check-service parses this with the shared UsersetRewrite model.
    assertThat(response.getConfigJson()).contains("viewer").contains("this");
  }

  @Test
  void unknown_namespace_is_reported_as_not_found() {
    assertThatThrownBy(
            () ->
                stub.getNamespaceConfig(
                    GetNamespaceConfigRequest.newBuilder().setNamespace("does-not-exist").build()))
        .isInstanceOf(StatusRuntimeException.class)
        .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
        .isEqualTo(Status.Code.NOT_FOUND);
  }
}
