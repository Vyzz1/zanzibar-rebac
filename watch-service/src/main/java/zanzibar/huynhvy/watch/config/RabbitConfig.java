package zanzibar.huynhvy.watch.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds watch-service to the shared tuple-changes fanout exchange through a <b>stream</b> queue: an
 * append-only, retained log rather than a classic queue whose messages vanish once consumed.
 *
 * <p>Reads from a stream are non-destructive and each consumer tracks its own offset, so several
 * instances (or test contexts) all receive every event — the competing-consumer problem that forced
 * per-instance anonymous queues for classic queues does not apply here.
 *
 * <p>Retention bounds the log; a Watch cursor older than {@code watch.stream.max-age} can no longer
 * be replayed. Consuming over plain AMQP requires a prefetch (basicQos), and the consumer's start
 * position is set with the {@code x-stream-offset} argument — {@code next} means "live only", which
 * matches today's behaviour.
 */
@Configuration
public class RabbitConfig {

  public static final String TUPLE_CHANGES_EXCHANGE = "tuple-changes";

  /**
   * Distinct from the old classic queue name so an existing broker doesn't reject the redeclare.
   */
  public static final String WATCH_STREAM = "tuple-changes.watch.stream";

  public static final String STREAM_LISTENER_FACTORY = "watchStreamListenerContainerFactory";

  @Bean
  public FanoutExchange tupleChangesExchange() {
    return ExchangeBuilder.fanoutExchange(TUPLE_CHANGES_EXCHANGE).durable(true).build();
  }

  @Bean
  public Queue watchStream(@Value("${watch.stream.max-age:24h}") String maxAge) {
    return QueueBuilder.durable(WATCH_STREAM).stream().withArgument("x-max-age", maxAge).build();
  }

  @Bean
  public Binding watchStreamBinding(Queue watchStream) {
    return BindingBuilder.bind(watchStream).to(tupleChangesExchange());
  }

  /** Stream consumers must set a prefetch and an explicit start offset. */
  @Bean(STREAM_LISTENER_FACTORY)
  public SimpleRabbitListenerContainerFactory watchStreamListenerContainerFactory(
      ConnectionFactory connectionFactory,
      @Value("${watch.stream.prefetch:100}") int prefetch,
      @Value("${watch.stream.offset:next}") String offset) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setPrefetchCount(prefetch);
    factory.setContainerCustomizer(
        container -> container.setConsumerArguments(Map.of("x-stream-offset", offset)));
    return factory;
  }
}
