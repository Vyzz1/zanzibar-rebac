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
import zanzibar.huynhvy.namespace.repository.NamespaceConfigRepository;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;

class UpsertNamespaceUseCaseTest {

  private static final Map<String, UsersetRewrite> RELATIONS = Map.of("viewer", new This());

  private NamespaceConfigRepository repository;
  private ValidateNamespaceUseCase validate;
  private UpsertNamespaceUseCase upsert;

  @BeforeEach
  void setUp() {
    repository = mock(NamespaceConfigRepository.class);
    validate = mock(ValidateNamespaceUseCase.class);
    upsert = new UpsertNamespaceUseCase(repository, validate, new ObjectMapper());
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
  }

  @Test
  void persists_the_relations_as_json() {
    when(repository.findTopByNamespaceOrderByVersionDesc("doc")).thenReturn(Optional.empty());
    ArgumentCaptor<NamespaceConfig> captor = ArgumentCaptor.forClass(NamespaceConfig.class);

    upsert.upsert("doc", RELATIONS);

    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getConfig()).contains("\"viewer\"").contains("\"this\"");
  }
}
