package zanzibar.huynhvy.tuplestore.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Meters for the write path, exported to Prometheus.
 *
 * <ul>
 *   <li>{@code zanzibar.tuples.written} / {@code zanzibar.tuples.deleted} — grants and revocations.
 *   <li>{@code zanzibar.outbox.published} — events drained to RabbitMQ. Compared against the two
 *       above it shows whether the outbox is keeping up; a persistent gap means the poller is
 *       falling behind or failing to publish.
 * </ul>
 */
@Component
public class TupleStoreMetrics {

  private final Counter tuplesWritten;
  private final Counter tuplesDeleted;
  private final Counter outboxPublished;

  public TupleStoreMetrics(MeterRegistry registry) {
    this.tuplesWritten =
        Counter.builder("zanzibar.tuples.written")
            .description("Relation tuples written")
            .register(registry);
    this.tuplesDeleted =
        Counter.builder("zanzibar.tuples.deleted")
            .description("Relation tuples deleted (revocations)")
            .register(registry);
    this.outboxPublished =
        Counter.builder("zanzibar.outbox.published")
            .description("Outbox events published to the tuple-changes exchange")
            .register(registry);
  }

  public void tupleWritten() {
    tuplesWritten.increment();
  }

  public void tuplesDeleted(int count) {
    tuplesDeleted.increment(count);
  }

  public void outboxPublished(int count) {
    outboxPublished.increment(count);
  }
}
