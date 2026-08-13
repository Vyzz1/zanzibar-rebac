package zanzibar.huynhvy.watch.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds watch-service's own durable queue to the shared tuple-changes fanout exchange, so it
 * receives every tuple change independently of other consumers (e.g. check-service).
 */
@Configuration
public class RabbitConfig {

  public static final String TUPLE_CHANGES_EXCHANGE = "tuple-changes";
  public static final String WATCH_QUEUE = "tuple-changes.watch";

  @Bean
  public FanoutExchange tupleChangesExchange() {
    return ExchangeBuilder.fanoutExchange(TUPLE_CHANGES_EXCHANGE).durable(true).build();
  }

  @Bean
  public Queue watchQueue() {
    return QueueBuilder.durable(WATCH_QUEUE).build();
  }

  @Bean
  public Binding watchBinding() {
    return BindingBuilder.bind(watchQueue()).to(tupleChangesExchange());
  }
}
