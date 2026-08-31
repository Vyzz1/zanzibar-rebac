package zanzibar.huynhvy.namespace.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zanzibar.huynhvy.namespace.domain.NamespaceConfig;
import zanzibar.huynhvy.namespace.domain.NamespaceView;
import zanzibar.huynhvy.namespace.domain.UpsertNamespaceUseCase;
import zanzibar.huynhvy.namespace.domain.ValidateNamespaceUseCase;
import zanzibar.huynhvy.namespace.exception.InvalidNamespaceConfigException;
import zanzibar.huynhvy.namespace.outbox.NamespaceOutboxEvent;
import zanzibar.huynhvy.namespace.outbox.NamespaceOutboxRepository;
import zanzibar.huynhvy.namespace.repository.NamespaceConfigRepository;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;

class UpsertNamespaceUseCaseTest {

  private static final Map<String, UsersetRewrite> RELATIONS = Map.of("viewer", new This());

  private NamespaceConfigRepository repository;
  private ValidateNamespaceUseCase validate;
  private NamespaceOutboxRepository outboxRepository;
  private UpsertNamespaceUseCase upsert;

  @BeforeEach
  void setUp() {
    repository = mock(NamespaceConfigRepository.class);
    validate = mock(ValidateNamespaceUseCase.class);
    outboxRepository = mock(NamespaceOutboxRepository.class);
    upsert = new UpsertNamespaceUseCase(repository, outboxRepository, validate, new ObjectMapper());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void first_write_creates_version_1_and_validates_first() {
    when(repository.findTopByNamespaceOrderByVersionDesc("doc")).thenReturn(Optional.empty());

    NamespaceView view = upsert.upsert("doc", RELATIONS);

    assertThat(view.version()).isEqualTo(1);
    verify(validate).validate(RELATIONS);
  }

  @Test
  void subsequent_write_appends_the_next_version() {
    when(repository.findTopByNamespaceOrderByVersionDesc("doc"))
        .thenReturn(Optional.of(NamespaceConfig.create("doc", 3, "{}")));

    assertThat(upsert.upsert("doc", RELATIONS).version()).isEqualTo(4);
  }

  @Test
  void does_not_persist_when_validation_fails() {
    doThrow(new InvalidNamespaceConfigException("bad")).when(validate).validate(any());

    assertThatThrownBy(() -> upsert.upsert("doc", RELATIONS))
        .isInstanceOf(InvalidNamespaceConfigException.class);
    verify(repository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void persists_the_relations_as_json() {
    when(repository.findTopByNamespaceOrderByVersionDesc("doc")).thenReturn(Optional.empty());
    ArgumentCaptor<NamespaceConfig> captor = ArgumentCaptor.forClass(NamespaceConfig.class);

    upsert.upsert("doc", RELATIONS);

    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getConfig()).contains("\"viewer\"").contains("\"this\"");
  }

  @Test
  void enqueues_a_config_change_event_naming_the_new_version() {
    when(repository.findTopByNamespaceOrderByVersionDesc("doc"))
        .thenReturn(Optional.of(NamespaceConfig.create("doc", 3, "{}")));
    ArgumentCaptor<NamespaceOutboxEvent> captor =
        ArgumentCaptor.forClass(NamespaceOutboxEvent.class);

    upsert.upsert("doc", RELATIONS);

    verify(outboxRepository).save(captor.capture());
    NamespaceOutboxEvent event = captor.getValue();
    assertThat(event.getEventType()).isEqualTo(NamespaceOutboxEvent.NAMESPACE_CONFIG_UPDATED);
    assertThat(event.getAggregateId()).isEqualTo("doc");
    assertThat(event.isPublished()).isFalse();
    // The version consumers compare against must be the one just written, not the previous one.
    assertThat(event.getPayload()).contains("\"namespace\":\"doc\"").contains("\"version\":4");
  }

  @Test
  void carries_no_rules_in_the_event() {
    when(repository.findTopByNamespaceOrderByVersionDesc("doc")).thenReturn(Optional.empty());
    ArgumentCaptor<NamespaceOutboxEvent> captor =
        ArgumentCaptor.forClass(NamespaceOutboxEvent.class);

    upsert.upsert("doc", RELATIONS);

    // A consumer re-reads the config; shipping it here would just be a second copy to go stale.
    verify(outboxRepository).save(captor.capture());
    assertThat(captor.getValue().getPayload()).doesNotContain("viewer");
  }
}
