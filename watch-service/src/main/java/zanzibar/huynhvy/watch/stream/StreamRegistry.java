package zanzibar.huynhvy.watch.stream;

import io.grpc.stub.StreamObserver;
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
