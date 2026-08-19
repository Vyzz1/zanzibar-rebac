package zanzibar.huynhvy.watch.stream;

import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.api.WatchEvent;

/**
 * Holds the live Watch streams, grouped by namespace, and fans a tuple-change event out to every
 * stream watching that namespace. Thread-safe: streams register/unregister from gRPC threads while
 * the RabbitMQ listener thread publishes. A stream whose {@code onNext} fails (client gone) is
 * dropped.
 */
@Slf4j
@Component
public class StreamRegistry {

  private final ConcurrentMap<String, Set<StreamObserver<WatchEvent>>> byNamespace =
      new ConcurrentHashMap<>();

  private final Counter eventsDelivered;

  public StreamRegistry(MeterRegistry registry) {
    Gauge.builder("zanzibar.watch.streams.active", this, StreamRegistry::totalSubscribers)
        .description("Open Watch streams on this instance")
        .register(registry);
    this.eventsDelivered =
        Counter.builder("zanzibar.watch.events.delivered")
            .description("Watch events pushed to subscribers")
            .register(registry);
  }

  /** Total open streams across all namespaces — backs the active-streams gauge. */
  public int totalSubscribers() {
    return byNamespace.values().stream().mapToInt(Set::size).sum();
  }

  public void register(String namespace, StreamObserver<WatchEvent> observer) {
    byNamespace.computeIfAbsent(namespace, key -> ConcurrentHashMap.newKeySet()).add(observer);
  }

  public void unregister(String namespace, StreamObserver<WatchEvent> observer) {
    Set<StreamObserver<WatchEvent>> observers = byNamespace.get(namespace);
    if (observers != null) {
      observers.remove(observer);
    }
  }

  public void publish(String namespace, WatchEvent event) {
    Set<StreamObserver<WatchEvent>> observers = byNamespace.get(namespace);
    if (observers == null) {
      return;
    }
    for (StreamObserver<WatchEvent> observer : observers) {
      try {
        observer.onNext(event);
        eventsDelivered.increment();
      } catch (RuntimeException e) {
        log.debug("Dropping a Watch stream that failed on delivery", e);
        unregister(namespace, observer);
      }
    }
  }

  /** Number of streams watching a namespace — for tests. */
  public int subscriberCount(String namespace) {
    Set<StreamObserver<WatchEvent>> observers = byNamespace.get(namespace);
    return observers == null ? 0 : observers.size();
  }
}
