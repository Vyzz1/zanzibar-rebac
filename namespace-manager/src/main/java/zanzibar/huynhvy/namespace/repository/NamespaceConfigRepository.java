package zanzibar.huynhvy.namespace.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import zanzibar.huynhvy.namespace.domain.NamespaceConfig;

public interface NamespaceConfigRepository extends JpaRepository<NamespaceConfig, Long> {

  /** The current (highest-version) config for a namespace, if any. */
  Optional<NamespaceConfig> findTopByNamespaceOrderByVersionDesc(String namespace);

  Optional<NamespaceConfig> findByNamespaceAndVersion(String namespace, int version);

  boolean existsByNamespace(String namespace);
}
