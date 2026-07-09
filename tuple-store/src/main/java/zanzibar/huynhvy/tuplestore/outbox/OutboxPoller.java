package zanzibar.huynhvy.tuplestore.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPoller {
  @Scheduled(fixedDelayString = "PT1M")
  public void poll() {}
}
