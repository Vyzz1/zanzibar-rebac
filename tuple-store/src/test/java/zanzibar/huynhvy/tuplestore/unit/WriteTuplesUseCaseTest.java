package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.domain.WriteTuplesUseCase;
import zanzibar.huynhvy.tuplestore.domain.ZookieMinter;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.OutboxRepository;
import zanzibar.huynhvy.tuplestore.repository.TupleWriteRepository;

class WriteTuplesUseCaseTest {

  private static final RelationTuple VALID =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");

  private TupleWriteRepository tupleWriteRepository;
  private OutboxRepository outboxRepository;
  private ZookieMinter zookieMinter;
  private WriteTuplesUseCase useCase;

  @BeforeEach
  void setUp() {
    tupleWriteRepository = mock(TupleWriteRepository.class);
    outboxRepository = mock(OutboxRepository.class);
    zookieMinter = mock(ZookieMinter.class);
    useCase =
        new WriteTuplesUseCase(
            tupleWriteRepository, outboxRepository, zookieMinter, new ObjectMapper());
  }

  @Test
  void rejects_empty_tuple_list() {
    assertThatThrownBy(() -> useCase.execute(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(tupleWriteRepository, outboxRepository);
  }

  @Test
  void rejects_null_tuple_list() {
    assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_tuple_with_a_blank_field_before_writing_anything() {
    RelationTuple bad = new RelationTuple("doc", "", "viewer", "user:bob");

    assertThatThrownBy(() -> useCase.execute(List.of(bad)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(tupleWriteRepository, outboxRepository);
  }

  @Test
  void writes_tuple_enqueues_outbox_and_mints_zookie_from_commit_timestamp() {
    OffsetDateTime commitTs = OffsetDateTime.of(2026, 7, 10, 12, 0, 0, 0, ZoneOffset.UTC);
    when(tupleWriteRepository.save(VALID)).thenReturn(commitTs);
    when(zookieMinter.mint(commitTs)).thenReturn(new Zookie("zk-token"));

    Zookie result = useCase.execute(List.of(VALID));

    assertThat(result.token()).isEqualTo("zk-token");
    verify(tupleWriteRepository).save(VALID);
    verify(zookieMinter).mint(commitTs);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(captor.capture());
    OutboxEvent event = captor.getValue();
    assertThat(event.getEventType()).isEqualTo("TUPLE_CREATED");
    assertThat(event.getPayload()).contains("report.pdf").contains("viewer");
    assertThat(event.isPublished()).isFalse();
  }
}
