package zanzibar.huynhvy.namespace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable version of a namespace's config, stored in {@code namespace.namespace_configs}. A
 * write never updates a row — it appends a new {@code version}. The {@code config} column is the
 * relations map JSON (a map of relation name to {@link
 * zanzibar.huynhvy.shared.domain.UsersetRewrite UsersetRewrite}).
 */
@Entity
@Table(name = "namespace_configs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceConfig {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String namespace;

  @Column(name = "version", nullable = false)
  private int version;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String config;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  /** Creates a new version row for a namespace with its (already validated) config JSON. */
  public static NamespaceConfig create(String namespace, int version, String config) {
    NamespaceConfig entity = new NamespaceConfig();
    entity.namespace = namespace;
    entity.version = version;
    entity.config = config;
    entity.createdAt = OffsetDateTime.now();
    return entity;
  }
}
