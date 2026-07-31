package zanzibar.huynhvy.check.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.api.GetNamespaceConfigRequest;
import zanzibar.huynhvy.api.GetNamespaceConfigResponse;
import zanzibar.huynhvy.api.NamespaceServiceGrpc;
import zanzibar.huynhvy.check.domain.NamespaceConfigView;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

/**
 * Fetches namespace config from namespace-manager over gRPC and parses the {@code config_json} with
 * the shared {@link UsersetRewrite} model. A {@code NOT_FOUND} surfaces as the raw {@link
 * io.grpc.StatusRuntimeException}; callers decide how to degrade.
 */
@Component
public class NamespaceConfigClient {

  private static final TypeReference<Map<String, UsersetRewrite>> RELATIONS_TYPE =
      new TypeReference<>() {};

  @GrpcClient("namespace-manager")
  private NamespaceServiceGrpc.NamespaceServiceBlockingStub stub;

  private final ObjectMapper objectMapper;

  public NamespaceConfigClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public NamespaceConfigView fetch(String namespace) {
    GetNamespaceConfigResponse response =
        stub.getNamespaceConfig(
            GetNamespaceConfigRequest.newBuilder().setNamespace(namespace).build());
    return new NamespaceConfigView(response.getVersion(), parse(response.getConfigJson()));
  }

  private Map<String, UsersetRewrite> parse(String json) {
    try {
      return objectMapper.readValue(json, RELATIONS_TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse namespace config json", e);
    }
  }
}
