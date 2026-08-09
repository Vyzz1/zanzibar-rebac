package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.cache.CacheKeyStrategy;
import zanzibar.huynhvy.shared.domain.RelationTuple;

class CacheKeyStrategyTest {

  private final CacheKeyStrategy strategy = new CacheKeyStrategy();

  @Test
  void builds_the_colon_joined_key() {
    assertThat(strategy.key(new RelationTuple("doc", "report.pdf", "viewer", "user:bob")))
        .isEqualTo("doc:report.pdf:viewer:user:bob");
  }
}
