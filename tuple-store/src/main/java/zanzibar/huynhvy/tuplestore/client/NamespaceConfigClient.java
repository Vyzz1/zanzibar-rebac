package zanzibar.huynhvy.tuplestore.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.api.GetNamespaceConfigRequest;
import zanzibar.huynhvy.api.GetNamespaceConfigResponse;
import zanzibar.huynhvy.api.NamespaceServiceGrpc;

/**
 * Reads a namespace's defined relation names from namespace-manager over gRPC, for validating
 * writes. Only the relation names are needed, so the config JSON's top-level keys are extracted
 * without parsing the full rewrite model.
 */
@Component
public class NamespaceConfigClient {

  @GrpcClient("namespace-manager")
  private NamespaceServiceGrpc.NamespaceServiceBlockingStub stub;

  private final ObjectMapper objectMapper;

  public NamespaceConfigClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * The relations defined for the namespace, or {@link Optional#empty()} if it has no config. A
   * transport error (namespace-manager unreachable) surfaces as {@link StatusRuntimeException}.
   */
  public Optional<Set<String>> definedRelations(String namespace) {
    try {
      GetNamespaceConfigResponse response =
          stub.getNamespaceConfig(
              GetNamespaceConfigRequest.newBuilder().setNamespace(namespace).build());
      return Optional.of(relationNames(response.getConfigJson()));
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
        return Optional.empty();
      }
      throw e;
    }
  }

  private Set<String> relationNames(String configJson) {
    try {
      JsonNode root = objectMapper.readTree(configJson);
      Set<String> names = new HashSet<>();
      root.fieldNames().forEachRemaining(names::add);
      return names;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse namespace config json", e);
    }
  }
}
