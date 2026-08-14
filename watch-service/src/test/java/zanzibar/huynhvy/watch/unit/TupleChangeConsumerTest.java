package zanzibar.huynhvy.watch.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.watch.rabbitmq.TupleChangeConsumer;
import zanzibar.huynhvy.watch.stream.StreamRegistry;

class TupleChangeConsumerTest {

  private static final String TUPLE_JSON =
      "{\"namespace\":\"doc\",\"objectId\":\"report.pdf\","
          + "\"relation\":\"viewer\",\"subjectId\":\"user:bob\"}";

  private final StreamRegistry registry = mock(StreamRegistry.class);
  private final TupleChangeConsumer consumer =
      new TupleChangeConsumer(new ObjectMapper(), registry);

  @Test
  void a_create_message_publishes_a_create_event_to_its_namespace() {
    consumer.consume(TUPLE_JSON, "CREATE");

    WatchEvent event = captured();
    assertThat(event.getOperation()).isEqualTo(WatchEvent.Operation.CREATE);
    assertThat(event.getTuple().getObjectId()).isEqualTo("report.pdf");
    assertThat(event.getTuple().getSubjectId()).isEqualTo("user:bob");
  }

  @Test
  void a_delete_message_publishes_a_delete_event() {
    consumer.consume(TUPLE_JSON, "DELETE");

    assertThat(captured().getOperation()).isEqualTo(WatchEvent.Operation.DELETE);
  }

  @Test
  void a_missing_operation_header_defaults_to_create() {
    consumer.consume(TUPLE_JSON, null);

    assertThat(captured().getOperation()).isEqualTo(WatchEvent.Operation.CREATE);
  }

  @Test
  void ignores_an_unparseable_message() {
    consumer.consume("not-json", "CREATE");

    verifyNoInteractions(registry);
  }

  private WatchEvent captured() {
    ArgumentCaptor<WatchEvent> captor = ArgumentCaptor.forClass(WatchEvent.class);
    verify(registry).publish(eq("doc"), captor.capture());
    return captor.getValue();
  }
}
