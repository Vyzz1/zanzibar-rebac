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

  private final StreamRegistry registry = mock(StreamRegistry.class);
  private final TupleChangeConsumer consumer =
      new TupleChangeConsumer(new ObjectMapper(), registry);

  @Test
  void parses_a_tuple_and_publishes_a_create_event_to_its_namespace() {
    consumer.consume(
        "{\"namespace\":\"doc\",\"objectId\":\"report.pdf\","
            + "\"relation\":\"viewer\",\"subjectId\":\"user:bob\"}");

    ArgumentCaptor<WatchEvent> captor = ArgumentCaptor.forClass(WatchEvent.class);
    verify(registry).publish(eq("doc"), captor.capture());
    WatchEvent event = captor.getValue();
    assertThat(event.getOperation()).isEqualTo(WatchEvent.Operation.CREATE);
    assertThat(event.getTuple().getObjectId()).isEqualTo("report.pdf");
    assertThat(event.getTuple().getRelation()).isEqualTo("viewer");
    assertThat(event.getTuple().getSubjectId()).isEqualTo("user:bob");
  }

  @Test
  void ignores_an_unparseable_message() {
    consumer.consume("not-json");

    verifyNoInteractions(registry);
  }
}
