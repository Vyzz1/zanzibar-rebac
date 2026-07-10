package zanzibar.huynhvy.namespace.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "namespace_configs")
public class NamespaceConfig {
  @Id private Long id;
}
