package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;

class OutboxEventTest {

  @Test
  void create_builds_an_unpublished_event_stamped_with_a_creation_time() {
    OutboxEvent event = OutboxEvent.create("agg-1", "TUPLE_CREATED", "{}");

    assertThat(event.getAggregateId()).isEqualTo("agg-1");
    assertThat(event.getEventType()).isEqualTo("TUPLE_CREATED");
    assertThat(event.getPayload()).isEqualTo("{}");
    assertThat(event.getCreatedAt()).isNotNull();
    assertThat(event.isPublished()).isFalse();
    assertThat(event.getPublishedAt()).isNull();
  }

  @Test
  void mark_published_sets_flag_and_timestamp() {
    OutboxEvent event = OutboxEvent.create("agg-1", "TUPLE_CREATED", "{}");
    OffsetDateTime at = OffsetDateTime.now();

    event.markPublished(at);

    assertThat(event.isPublished()).isTrue();
    assertThat(event.getPublishedAt()).isEqualTo(at);
  }
}
