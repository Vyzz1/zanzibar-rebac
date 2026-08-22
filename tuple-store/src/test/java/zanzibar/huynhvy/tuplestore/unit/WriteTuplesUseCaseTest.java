package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.domain.NamespaceRelationsProvider;
import zanzibar.huynhvy.tuplestore.domain.WriteTuplesUseCase;
import zanzibar.huynhvy.tuplestore.domain.ZookieMinter;
import zanzibar.huynhvy.tuplestore.metrics.TupleStoreMetrics;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.OutboxRepository;
import zanzibar.huynhvy.tuplestore.repository.TupleWriteRepository;

class WriteTuplesUseCaseTest {

  private static final RelationTuple VALID =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");
  private static final OffsetDateTime COMMIT_TS =
      OffsetDateTime.of(2026, 7, 10, 12, 0, 0, 0, ZoneOffset.UTC);

  private TupleWriteRepository tupleWriteRepository;
  private OutboxRepository outboxRepository;
  private ZookieMinter zookieMinter;
  private NamespaceRelationsProvider relationsProvider;
  private WriteTuplesUseCase useCase;

  @BeforeEach
  void setUp() {
    tupleWriteRepository = mock(TupleWriteRepository.class);
    outboxRepository = mock(OutboxRepository.class);
    zookieMinter = mock(ZookieMinter.class);
    relationsProvider = mock(NamespaceRelationsProvider.class);
    // No config known by default → writes are permitted.
    when(relationsProvider.definedRelations(any())).thenReturn(Optional.empty());
    useCase =
        new WriteTuplesUseCase(
            tupleWriteRepository,
            outboxRepository,
            zookieMinter,
            new ObjectMapper(),
            relationsProvider,
            new TupleStoreMetrics(new SimpleMeterRegistry()),
            true);
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
  void rejects_a_relation_not_defined_in_the_namespace_config() {
    // config exists for "doc" but only defines "editor" — "viewer" is undefined.
    when(relationsProvider.definedRelations("doc")).thenReturn(Optional.of(Set.of("editor")));

    assertThatThrownBy(() -> useCase.execute(List.of(VALID)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("viewer");
    verifyNoInteractions(tupleWriteRepository, outboxRepository);
  }

  @Test
  void writes_when_the_relation_is_defined_in_the_config() {
    when(relationsProvider.definedRelations("doc"))
        .thenReturn(Optional.of(Set.of("viewer", "editor")));
    stubWrite();

    useCase.execute(List.of(VALID));

    verify(tupleWriteRepository).save(VALID);
  }

  @Test
  void writes_tuple_enqueues_outbox_and_mints_zookie_from_commit_timestamp() {
    stubWrite();

    Zookie result = useCase.execute(List.of(VALID));

    assertThat(result.token()).isEqualTo("zk-token");
    verify(tupleWriteRepository).save(VALID);
    verify(zookieMinter).mint(any());

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(captor.capture());
    OutboxEvent event = captor.getValue();
    assertThat(event.getEventType()).isEqualTo("TUPLE_CREATED");
    assertThat(event.getPayload()).contains("report.pdf").contains("viewer");
    assertThat(event.isPublished()).isFalse();
    // Consumers mint their resume token from this, so it must be the database's commit moment.
    assertThat(event.getCommitTimestamp()).isEqualTo(COMMIT_TS);
  }

  @Test
  void validation_is_skipped_when_disabled() {
    WriteTuplesUseCase noValidation =
        new WriteTuplesUseCase(
            tupleWriteRepository,
            outboxRepository,
            zookieMinter,
            new ObjectMapper(),
            relationsProvider,
            new TupleStoreMetrics(new SimpleMeterRegistry()),
            false);
    stubWrite();

    noValidation.execute(List.of(VALID));

    verify(tupleWriteRepository).save(VALID);
    verifyNoInteractions(relationsProvider);
  }

  private void stubWrite() {
    when(tupleWriteRepository.save(VALID)).thenReturn(COMMIT_TS);
    when(zookieMinter.mint(COMMIT_TS)).thenReturn(new Zookie("zk-token"));
  }
}
