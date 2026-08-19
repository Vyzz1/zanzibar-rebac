package zanzibar.huynhvy.watch.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.watch.config.RabbitConfig;
import zanzibar.huynhvy.watch.stream.StreamRegistry;

/**
 * Consumes tuple-change messages published by tuple-store (the JSON of a {@link RelationTuple},
 * with an {@code operation} header) and fans each out to the Watch streams for that namespace.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TupleChangeConsumer {

  private final ObjectMapper objectMapper;
  private final StreamRegistry registry;

  @RabbitListener(
      queues = RabbitConfig.WATCH_STREAM,
      containerFactory = RabbitConfig.STREAM_LISTENER_FACTORY)
  public void consume(
      String message, @Header(name = "operation", required = false) String operation) {
    RelationTuple tuple;
    try {
      tuple = objectMapper.readValue(message, RelationTuple.class);
    } catch (JsonProcessingException e) {
      log.warn("Discarding unparseable tuple-change message: {}", message, e);
      return;
    }

    WatchEvent event =
        WatchEvent.newBuilder()
            .setOperation(operationOf(operation))
            .setTuple(
                zanzibar.huynhvy.api.RelationTuple.newBuilder()
                    .setNamespace(tuple.namespace())
                    .setObjectId(tuple.objectId())
                    .setRelation(tuple.relation())
                    .setSubjectId(tuple.subjectId())
                    .build())
            .build();

    registry.publish(tuple.namespace(), event);
  }

  private WatchEvent.Operation operationOf(String operation) {
    if ("DELETE".equals(operation)) {
      return WatchEvent.Operation.DELETE;
    }
    // Absent header (older messages) defaults to CREATE; an unexpected value is logged.
    if (operation != null && !"CREATE".equals(operation)) {
      log.warn("Unknown operation header '{}'; treating as CREATE", operation);
    }
    return WatchEvent.Operation.CREATE;
  }
}
