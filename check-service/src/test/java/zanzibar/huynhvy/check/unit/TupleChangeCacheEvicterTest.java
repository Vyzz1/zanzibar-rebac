package zanzibar.huynhvy.check.unit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.cache.TupleCache;
import zanzibar.huynhvy.check.rabbitmq.TupleChangeCacheEvicter;

class TupleChangeCacheEvicterTest {

  private final TupleCache tupleCache = mock(TupleCache.class);
  private final TupleChangeCacheEvicter evicter =
      new TupleChangeCacheEvicter(new ObjectMapper(), tupleCache);

  @Test
  void evicts_the_object_of_the_changed_tuple() {
    evicter.onTupleChange(
        "{\"namespace\":\"doc\",\"objectId\":\"report.pdf\","
            + "\"relation\":\"editor\",\"subjectId\":\"user:alice\"}");

    verify(tupleCache).evictObject("doc", "report.pdf");
  }

  @Test
  void ignores_an_unparseable_message() {
    evicter.onTupleChange("not-json");

    verifyNoInteractions(tupleCache);
  }
}
