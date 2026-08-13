package zanzibar.huynhvy.check.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds check-service's own durable queue to the shared tuple-changes fanout exchange, so it
 * receives every tuple change (to invalidate the cache) independently of watch-service.
 */
@Configuration
public class RabbitConfig {

  public static final String TUPLE_CHANGES_EXCHANGE = "tuple-changes";
  public static final String CHECK_QUEUE = "tuple-changes.check";

  @Bean
  public FanoutExchange tupleChangesExchange() {
    return ExchangeBuilder.fanoutExchange(TUPLE_CHANGES_EXCHANGE).durable(true).build();
  }

  @Bean
  public Queue checkCacheQueue() {
    return QueueBuilder.durable(CHECK_QUEUE).build();
  }

  @Bean
  public Binding checkCacheBinding() {
    return BindingBuilder.bind(checkCacheQueue()).to(tupleChangesExchange());
  }
}
