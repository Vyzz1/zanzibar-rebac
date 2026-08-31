package zanzibar.huynhvy.namespace.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA mapping of {@code namespace.outbox_events}. Rows are inserted in the same transaction as the
 * config write and later drained by {@link NamespaceOutboxPoller}.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
public class NamespaceOutboxEvent {

  /** The only event type today; named so a second one can be added without ambiguity. */
  public static final String NAMESPACE_CONFIG_UPDATED = "NAMESPACE_CONFIG_UPDATED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private boolean published;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  /** Creates a new, unpublished config-change event. */
  public static NamespaceOutboxEvent configUpdated(String namespace, String payload) {
    NamespaceOutboxEvent event = new NamespaceOutboxEvent();
    event.aggregateId = namespace;
    event.eventType = NAMESPACE_CONFIG_UPDATED;
    event.payload = payload;
    event.createdAt = OffsetDateTime.now();
    event.published = false;
    return event;
  }

  /** Marks this event as published at the given instant. */
  public void markPublished(OffsetDateTime at) {
    this.published = true;
    this.publishedAt = at;
  }
}
