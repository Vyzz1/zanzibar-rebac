package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.domain.DeleteTuplesUseCase;
import zanzibar.huynhvy.tuplestore.domain.ZookieMinter;
import zanzibar.huynhvy.tuplestore.metrics.TupleStoreMetrics;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.OutboxRepository;
import zanzibar.huynhvy.tuplestore.repository.TupleWriteRepository;

class DeleteTuplesUseCaseTest {

  private static final RelationTuple TUPLE =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");
  private static final OffsetDateTime NOW =
      OffsetDateTime.of(2026, 8, 13, 12, 0, 0, 0, ZoneOffset.UTC);

  private TupleWriteRepository tupleWriteRepository;
  private OutboxRepository outboxRepository;
  private ZookieMinter zookieMinter;
  private DeleteTuplesUseCase useCase;

  @BeforeEach
  void setUp() {
    tupleWriteRepository = mock(TupleWriteRepository.class);
    outboxRepository = mock(OutboxRepository.class);
    zookieMinter = mock(ZookieMinter.class);
    when(tupleWriteRepository.currentTimestamp()).thenReturn(NOW);
    when(zookieMinter.mint(NOW)).thenReturn(new Zookie("zk-token"));
    useCase =
        new DeleteTuplesUseCase(
            tupleWriteRepository,
            outboxRepository,
            zookieMinter,
            new ObjectMapper(),
            new TupleStoreMetrics(new SimpleMeterRegistry()));
  }

  @Test
  void deletes_the_row_enqueues_a_delete_event_and_mints_a_zookie() {
    when(tupleWriteRepository.delete(TUPLE)).thenReturn(1);

    Zookie result = useCase.execute(List.of(TUPLE));

    assertThat(result.token()).isEqualTo("zk-token");
    verify(tupleWriteRepository).delete(TUPLE);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("TUPLE_DELETED");
    assertThat(captor.getValue().getPayload()).contains("report.pdf").contains("viewer");
    // The event carries the same moment the returned Zookie names, so a client resuming from the
    // event lands exactly where a client resuming from the write's Zookie would.
    assertThat(captor.getValue().getCommitTimestamp()).isEqualTo(NOW);
  }

  @Test
  void deleting_a_missing_tuple_enqueues_no_event_but_still_returns_a_zookie() {
    when(tupleWriteRepository.delete(TUPLE)).thenReturn(0);

    assertThat(useCase.execute(List.of(TUPLE)).token()).isEqualTo("zk-token");

    verifyNoInteractions(outboxRepository);
  }

  @Test
  void rejects_an_empty_list() {
    assertThatThrownBy(() -> useCase.execute(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(outboxRepository);
  }

  @Test
  void rejects_a_tuple_with_a_blank_field() {
    RelationTuple bad = new RelationTuple("doc", "", "viewer", "user:bob");

    assertThatThrownBy(() -> useCase.execute(List.of(bad)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(tupleWriteRepository, never()).delete(any());
  }
}
