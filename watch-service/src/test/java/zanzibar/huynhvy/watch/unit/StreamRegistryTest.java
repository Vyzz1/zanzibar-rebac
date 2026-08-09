package zanzibar.huynhvy.watch.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.api.RelationTuple;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.watch.stream.StreamRegistry;

class StreamRegistryTest {

  private final StreamRegistry registry = new StreamRegistry();

  @Test
  void fans_out_only_to_streams_watching_the_namespace() {
    StreamObserver<WatchEvent> doc = observer();
    StreamObserver<WatchEvent> folder = observer();
    registry.register("doc", doc);
    registry.register("folder", folder);

    WatchEvent event = createEvent("doc");
    registry.publish("doc", event);

    verify(doc).onNext(event);
    verifyNoInteractions(folder);
  }

  @Test
  void an_unregistered_stream_stops_receiving() {
    StreamObserver<WatchEvent> doc = observer();
    registry.register("doc", doc);
    registry.unregister("doc", doc);

    registry.publish("doc", createEvent("doc"));

    verify(doc, never()).onNext(any());
    assertThat(registry.subscriberCount("doc")).isZero();
  }

  @Test
  void a_stream_that_fails_on_delivery_is_dropped() {
    StreamObserver<WatchEvent> flaky = observer();
    doThrow(new RuntimeException("client gone")).when(flaky).onNext(any());
    registry.register("doc", flaky);

    registry.publish("doc", createEvent("doc"));

    assertThat(registry.subscriberCount("doc")).isZero();
  }

  @SuppressWarnings("unchecked")
  private static StreamObserver<WatchEvent> observer() {
    return mock(StreamObserver.class);
  }

  private static WatchEvent createEvent(String namespace) {
    return WatchEvent.newBuilder()
        .setOperation(WatchEvent.Operation.CREATE)
        .setTuple(RelationTuple.newBuilder().setNamespace(namespace).build())
        .build();
  }
}
