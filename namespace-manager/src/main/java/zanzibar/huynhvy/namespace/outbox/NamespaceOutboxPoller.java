package zanzibar.huynhvy.namespace.outbox;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zanzibar.huynhvy.namespace.rabbitmq.NamespaceEventPublisher;

@Slf4j
@Component
@RequiredArgsConstructor
public class NamespaceOutboxPoller {

  private static final int BATCH_SIZE = 100;

  private final NamespaceOutboxRepository outboxRepository;
  private final NamespaceEventPublisher publisher;

  /**
   * Drains a batch of unpublished events every poll interval. The whole batch runs in one
   * transaction so the {@code FOR UPDATE SKIP LOCKED} row locks are held until the events are
   * published and marked, letting multiple instances run safely.
   */
  @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
  @Transactional
  public void poll() {
    List<NamespaceOutboxEvent> batch = outboxRepository.findUnpublishedForUpdate(BATCH_SIZE);
    if (batch.isEmpty()) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    for (NamespaceOutboxEvent event : batch) {
      publisher.publish(event);
      event.markPublished(now); // flushed on commit via dirty checking
    }
    log.debug("Published {} namespace outbox event(s)", batch.size());
  }
}
