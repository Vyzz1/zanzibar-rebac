package zanzibar.huynhvy.namespace.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zanzibar.huynhvy.namespace.exception.NamespaceNotFoundException;
import zanzibar.huynhvy.namespace.repository.NamespaceConfigRepository;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

/** Reads a stored namespace config (latest or a specific version) and parses its relations. */
@Service
@RequiredArgsConstructor
public class GetNamespaceUseCase {

  private static final TypeReference<Map<String, UsersetRewrite>> RELATIONS_TYPE =
      new TypeReference<>() {};

  private final NamespaceConfigRepository repository;
  private final ObjectMapper objectMapper;

  public NamespaceView getLatest(String namespace) {
    return toView(
        repository
            .findTopByNamespaceOrderByVersionDesc(namespace)
            .orElseThrow(() -> new NamespaceNotFoundException(namespace)));
  }

  public NamespaceView getVersion(String namespace, int version) {
    return toView(
        repository
            .findByNamespaceAndVersion(namespace, version)
            .orElseThrow(() -> new NamespaceNotFoundException(namespace, version)));
  }

  private NamespaceView toView(NamespaceConfig config) {
    return new NamespaceView(
        config.getNamespace(),
        config.getVersion(),
        fromJson(config.getConfig()),
        config.getCreatedAt());
  }

  private Map<String, UsersetRewrite> fromJson(String json) {
    try {
      return objectMapper.readValue(json, RELATIONS_TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to read stored namespace config", e);
    }
  }
}
