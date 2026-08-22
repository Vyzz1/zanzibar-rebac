package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zanzibar.huynhvy.tuplestore.metrics.TupleStoreMetrics;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.OutboxPoller;
import zanzibar.huynhvy.tuplestore.outbox.OutboxRepository;
import zanzibar.huynhvy.tuplestore.rabbitmq.TupleEventPublisher;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

  @Mock private OutboxRepository outboxRepository;

  @Mock private TupleEventPublisher publisher;

  @Mock private TupleStoreMetrics metrics;

  @InjectMocks private OutboxPoller poller;

  @Test
  void does_nothing_when_there_are_no_unpublished_events() {
    when(outboxRepository.findUnpublishedForUpdate(anyInt())).thenReturn(List.of());

    poller.poll();

    verifyNoInteractions(publisher);
  }

  @Test
  void publishes_and_marks_every_fetched_event() {
    OutboxEvent first = OutboxEvent.create("a", "TUPLE_CREATED", "{}", OffsetDateTime.now());
    OutboxEvent second = OutboxEvent.create("b", "TUPLE_CREATED", "{}", OffsetDateTime.now());
    when(outboxRepository.findUnpublishedForUpdate(anyInt())).thenReturn(List.of(first, second));

    poller.poll();

    verify(publisher).publish(first);
    verify(publisher).publish(second);
    assertThat(first.isPublished()).isTrue();
    assertThat(second.isPublished()).isTrue();
  }
}
