package zanzibar.huynhvy.check.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds check-service to the two fanout exchanges it invalidates from — tuple changes and namespace
 * config changes — each via its own exclusive, auto-delete queue. Each instance gets its own queue
 * so every instance receives every event (to invalidate its cache) — rather than instances
 * competing for one shared queue, which would let a message evict only one instance's cache.
 */
@Configuration
public class RabbitConfig {

  public static final String TUPLE_CHANGES_EXCHANGE = "tuple-changes";
  public static final String NAMESPACE_CHANGES_EXCHANGE = "namespace-changes";

  @Bean
  public FanoutExchange tupleChangesExchange() {
    return ExchangeBuilder.fanoutExchange(TUPLE_CHANGES_EXCHANGE).durable(true).build();
  }

  @Bean
  public Queue checkCacheQueue() {
    return new AnonymousQueue();
  }

  @Bean
  public Binding checkCacheBinding() {
    return BindingBuilder.bind(checkCacheQueue()).to(tupleChangesExchange());
  }

  @Bean
  public FanoutExchange namespaceChangesExchange() {
    return ExchangeBuilder.fanoutExchange(NAMESPACE_CHANGES_EXCHANGE).durable(true).build();
  }

  /** Own queue per instance, for the same reason as the tuple one: every instance must be told. */
  @Bean
  public Queue namespaceChangeQueue() {
    return new AnonymousQueue();
  }

  @Bean
  public Binding namespaceChangeBinding() {
    return BindingBuilder.bind(namespaceChangeQueue()).to(namespaceChangesExchange());
  }
}
