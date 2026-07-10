package zanzibar.huynhvy.tuplestore.outbox;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zanzibar.huynhvy.tuplestore.rabbitmq.TupleEventPublisher;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

  private static final int BATCH_SIZE = 100;

  private final OutboxRepository outboxRepository;
  private final TupleEventPublisher publisher;

  /**
   * Drains a batch of unpublished events every poll interval. The whole batch runs in one
   * transaction so the {@code FOR UPDATE SKIP LOCKED} row locks are held until the events are
   * published and marked, letting multiple instances run safely.
   */
  @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
  @Transactional
  public void poll() {
    List<OutboxEvent> batch = outboxRepository.findUnpublishedForUpdate(BATCH_SIZE);
    if (batch.isEmpty()) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    for (OutboxEvent event : batch) {
      publisher.publish(event);
      event.markPublished(now); // flushed on commit via dirty checking
    }
    log.debug("Published {} outbox event(s)", batch.size());
  }
}
