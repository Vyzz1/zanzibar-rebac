package zanzibar.huynhvy.namespace.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zanzibar.huynhvy.namespace.repository.NamespaceConfigRepository;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

/**
 * Validates a submitted config and stores it as the next version of the namespace (append-only —
 * the previous versions are kept).
 */
@Service
@RequiredArgsConstructor
public class UpsertNamespaceUseCase {

  private static final TypeReference<Map<String, UsersetRewrite>> RELATIONS_TYPE =
      new TypeReference<>() {};

  private final NamespaceConfigRepository repository;
  private final ValidateNamespaceUseCase validateNamespace;
  private final ObjectMapper objectMapper;

  @Transactional
  public NamespaceView upsert(String namespace, Map<String, UsersetRewrite> relations) {
    validateNamespace.validate(relations);

    int nextVersion =
        repository
            .findTopByNamespaceOrderByVersionDesc(namespace)
            .map(existing -> existing.getVersion() + 1)
            .orElse(1);

    NamespaceConfig saved =
        repository.save(NamespaceConfig.create(namespace, nextVersion, toJson(relations)));

    return new NamespaceView(
        saved.getNamespace(), saved.getVersion(), relations, saved.getCreatedAt());
  }

  private String toJson(Map<String, UsersetRewrite> relations) {
    try {
      // writerFor with the explicit type keeps the polymorphic tag on each rewrite; a plain
      // writeValueAsString loses the generic type to erasure and drops the tag.
      return objectMapper.writerFor(RELATIONS_TYPE).writeValueAsString(relations);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize namespace config", e);
    }
  }
}
