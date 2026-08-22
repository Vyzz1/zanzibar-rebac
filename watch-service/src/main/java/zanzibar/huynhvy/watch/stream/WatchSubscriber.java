package zanzibar.huynhvy.watch.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.watch.config.RabbitConfig;

/**
 * Gives each Watch call its own consumer on the tuple-change stream, starting at that client's
 * cursor. Because stream reads are non-destructive and offsets are per-consumer, one client
 * replaying history does not affect another reading live.
 *
 * <p>The stream carries every namespace, so each subscription filters to the one it watches.
 */
@Slf4j
@Component
public class WatchSubscriber {

  // Set by tuple-store when it publishes. Duplicated rather than imported — services share a wire
  // contract, never a Maven module.
  private static final String OPERATION_HEADER = "operation";
  private static final String ZOOKIE_HEADER = "zookie";

  private final ConnectionFactory connectionFactory;
  private final ObjectMapper objectMapper;
  private final int prefetch;
  private final AtomicInteger activeStreams = new AtomicInteger();
  private final Counter eventsDelivered;

  public WatchSubscriber(
      ConnectionFactory connectionFactory,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Value("${watch.stream.prefetch:100}") int prefetch) {
    this.connectionFactory = connectionFactory;
    this.objectMapper = objectMapper;
    this.prefetch = prefetch;
    Gauge.builder("zanzibar.watch.streams.active", activeStreams, AtomicInteger::get)
        .description("Open Watch streams on this instance")
        .register(meterRegistry);
    this.eventsDelivered =
        Counter.builder("zanzibar.watch.events.delivered")
            .description("Watch events pushed to subscribers")
            .register(meterRegistry);
  }

  /**
   * Starts delivering changes for {@code namespace} from {@code cursor}. Close the handle to stop
   * the consumer — the caller does that when the gRPC client goes away.
   */
  public AutoCloseable subscribe(
      String namespace, WatchCursor cursor, StreamObserver<WatchEvent> observer) {
    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setQueueNames(RabbitConfig.WATCH_STREAM);
    container.setPrefetchCount(prefetch);
    container.setConsumerArguments(Map.of("x-stream-offset", cursor.streamOffset()));
    container.setMessageListener(
        (MessageListener) message -> deliver(namespace, message, observer));
    container.start();

    activeStreams.incrementAndGet();
    log.debug("Watch on '{}' subscribed from {}", namespace, cursor.streamOffset());
    return () -> {
      container.stop();
      activeStreams.decrementAndGet();
    };
  }

  private void deliver(String namespace, Message message, StreamObserver<WatchEvent> observer) {
    RelationTuple tuple;
    try {
      tuple = objectMapper.readValue(message.getBody(), RelationTuple.class);
    } catch (IOException e) {
      log.warn("Discarding unparseable tuple-change message", e);
      return;
    }
    if (!namespace.equals(tuple.namespace())) {
      return; // the stream carries every namespace
    }

    Object operation = message.getMessageProperties().getHeader(OPERATION_HEADER);
    Object zookie = message.getMessageProperties().getHeader(ZOOKIE_HEADER);
    try {
      observer.onNext(
          toEvent(
              tuple,
              operation == null ? null : operation.toString(),
              zookie == null ? null : zookie.toString()));
      eventsDelivered.increment();
    } catch (RuntimeException e) {
      // The client is gone; its cancel handler stops this container shortly.
      log.debug("Watch delivery failed, dropping event", e);
    }
  }

  /**
   * The Zookie is copied straight from the header, never minted here: only tuple-store holds the
   * authority to sign one, and only it knows when the change actually committed. An event published
   * before the outbox carried that timestamp arrives without one, and is delivered with the field
   * left empty rather than with a guessed token a client would resume from wrongly.
   */
  private WatchEvent toEvent(RelationTuple tuple, String operation, String zookie) {
    WatchEvent.Builder event =
        WatchEvent.newBuilder()
            .setOperation(operationOf(operation))
            .setTuple(
                zanzibar.huynhvy.api.RelationTuple.newBuilder()
                    .setNamespace(tuple.namespace())
                    .setObjectId(tuple.objectId())
                    .setRelation(tuple.relation())
                    .setSubjectId(tuple.subjectId())
                    .build());
    if (zookie != null) {
      event.setZookie(zookie);
    }
    return event.build();
  }

  private WatchEvent.Operation operationOf(String operation) {
    if ("DELETE".equals(operation)) {
      return WatchEvent.Operation.DELETE;
    }
    if (operation != null && !"CREATE".equals(operation)) {
      log.warn("Unknown operation header '{}'; treating as CREATE", operation);
    }
    return WatchEvent.Operation.CREATE;
  }
}
