package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;

class OutboxEventTest {

  private static final OffsetDateTime COMMITTED_AT =
      OffsetDateTime.of(2026, 8, 13, 12, 0, 0, 0, ZoneOffset.UTC);

  @Test
  void create_builds_an_unpublished_event_stamped_with_a_creation_time() {
    OutboxEvent event = OutboxEvent.create("agg-1", "TUPLE_CREATED", "{}", COMMITTED_AT);

    assertThat(event.getAggregateId()).isEqualTo("agg-1");
    assertThat(event.getEventType()).isEqualTo("TUPLE_CREATED");
    assertThat(event.getPayload()).isEqualTo("{}");
    assertThat(event.getCreatedAt()).isNotNull();
    assertThat(event.isPublished()).isFalse();
    assertThat(event.getPublishedAt()).isNull();
  }

  @Test
  void create_keeps_the_commit_timestamp_apart_from_the_row_creation_time() {
    OutboxEvent event = OutboxEvent.create("agg-1", "TUPLE_CREATED", "{}", COMMITTED_AT);

    // The resume token is minted from the commit, never from when this row happened to be built.
    assertThat(event.getCommitTimestamp()).isEqualTo(COMMITTED_AT);
    assertThat(event.getCreatedAt()).isNotEqualTo(COMMITTED_AT);
  }

  @Test
  void mark_published_sets_flag_and_timestamp() {
    OutboxEvent event = OutboxEvent.create("agg-1", "TUPLE_CREATED", "{}", COMMITTED_AT);
    OffsetDateTime at = OffsetDateTime.now();

    event.markPublished(at);

    assertThat(event.isPublished()).isTrue();
    assertThat(event.getPublishedAt()).isEqualTo(at);
  }
}
