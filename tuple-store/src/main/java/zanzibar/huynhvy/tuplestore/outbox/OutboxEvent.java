package zanzibar.huynhvy.tuplestore.outbox;

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
 * JPA mapping of the {@code tuplestore.outbox_events} table. Rows are inserted in the same
 * transaction as the tuple write and later drained by {@link OutboxPoller}.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

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

  /** Creates a new, unpublished event stamped with the current time. */
  public static OutboxEvent create(String aggregateId, String eventType, String payload) {
    OutboxEvent event = new OutboxEvent();
    event.aggregateId = aggregateId;
    event.eventType = eventType;
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
