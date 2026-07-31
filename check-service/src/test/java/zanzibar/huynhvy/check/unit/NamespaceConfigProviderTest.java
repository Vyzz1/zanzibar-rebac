package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.client.NamespaceConfigClient;
import zanzibar.huynhvy.check.domain.NamespaceConfigProvider;
import zanzibar.huynhvy.check.domain.NamespaceConfigView;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

class NamespaceConfigProviderTest {

  private static final NamespaceConfigView DOC_CONFIG =
      new NamespaceConfigView(1, Map.of("viewer", new UsersetRewrite.This()));

  @Test
  void caches_within_the_ttl_and_hits_the_client_once() {
    NamespaceConfigClient client = mock(NamespaceConfigClient.class);
    when(client.fetch("doc")).thenReturn(DOC_CONFIG);
    NamespaceConfigProvider provider = new NamespaceConfigProvider(client, 60);

    assertThat(provider.get("doc")).isEqualTo(DOC_CONFIG);
    assertThat(provider.get("doc")).isEqualTo(DOC_CONFIG);

    verify(client, times(1)).fetch("doc");
  }

  @Test
  void refetches_when_the_ttl_is_zero() {
    NamespaceConfigClient client = mock(NamespaceConfigClient.class);
    when(client.fetch("doc")).thenReturn(DOC_CONFIG);
    NamespaceConfigProvider provider = new NamespaceConfigProvider(client, 0);

    provider.get("doc");
    provider.get("doc");

    verify(client, times(2)).fetch("doc");
  }

  @Test
  void a_missing_namespace_degrades_to_empty_config() {
    NamespaceConfigClient client = mock(NamespaceConfigClient.class);
    when(client.fetch("ghost")).thenThrow(Status.NOT_FOUND.asRuntimeException());
    NamespaceConfigProvider provider = new NamespaceConfigProvider(client, 60);

    assertThat(provider.get("ghost")).isEqualTo(NamespaceConfigView.EMPTY);
  }

  @Test
  void a_non_not_found_error_propagates() {
    NamespaceConfigClient client = mock(NamespaceConfigClient.class);
    when(client.fetch("doc")).thenThrow(Status.UNAVAILABLE.asRuntimeException());
    NamespaceConfigProvider provider = new NamespaceConfigProvider(client, 60);

    assertThatThrownBy(() -> provider.get("doc")).isInstanceOf(StatusRuntimeException.class);
  }
}
