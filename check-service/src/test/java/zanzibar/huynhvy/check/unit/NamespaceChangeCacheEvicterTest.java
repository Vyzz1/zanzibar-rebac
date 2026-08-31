package zanzibar.huynhvy.check.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.cache.TupleCache;
import zanzibar.huynhvy.check.domain.NamespaceConfigProvider;
import zanzibar.huynhvy.check.rabbitmq.NamespaceChangeCacheEvicter;

class NamespaceChangeCacheEvicterTest {

  private final NamespaceConfigProvider configProvider = mock(NamespaceConfigProvider.class);
  private final TupleCache tupleCache = mock(TupleCache.class);
  private final NamespaceChangeCacheEvicter evicter =
      new NamespaceChangeCacheEvicter(new ObjectMapper(), configProvider, tupleCache);

  @Test
  void drops_both_the_cached_config_and_the_answers_derived_from_it() {
    evicter.onNamespaceChange("{\"namespace\":\"doc\",\"version\":4}");

    // Dropping only one of the two would leave the stale rules in play: keep the config and the
    // answers get recomputed from it; keep the answers and they are served as-is.
    verify(configProvider).evict("doc");
    verify(tupleCache).evictNamespace("doc");
  }

  @Test
  void leaves_other_namespaces_alone() {
    evicter.onNamespaceChange("{\"namespace\":\"folder\",\"version\":1}");

    verify(configProvider).evict("folder");
    verify(tupleCache).evictNamespace("folder");
  }

  @Test
  void discards_an_unparseable_message_without_evicting_anything() {
    evicter.onNamespaceChange("not json");

    verifyNoInteractions(configProvider, tupleCache);
  }

  @Test
  void ignores_unknown_fields_so_a_newer_producer_does_not_break_it() {
    evicter.onNamespaceChange("{\"namespace\":\"doc\",\"version\":4,\"somethingNew\":true}");

    verify(configProvider).evict("doc");
    verify(tupleCache).evictNamespace(any());
  }
}
