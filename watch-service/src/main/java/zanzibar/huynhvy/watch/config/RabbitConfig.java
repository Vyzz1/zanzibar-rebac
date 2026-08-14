package zanzibar.huynhvy.watch.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds watch-service to the shared tuple-changes fanout exchange via its own exclusive,
 * auto-delete queue. Each instance gets its own queue so every instance receives every event (its
 * Watch streams are held in-memory per instance) — rather than instances competing for one shared
 * queue.
 */
@Configuration
public class RabbitConfig {

  public static final String TUPLE_CHANGES_EXCHANGE = "tuple-changes";

  @Bean
  public FanoutExchange tupleChangesExchange() {
    return ExchangeBuilder.fanoutExchange(TUPLE_CHANGES_EXCHANGE).durable(true).build();
  }

  @Bean
  public Queue watchQueue() {
    return new AnonymousQueue();
  }

  @Bean
  public Binding watchBinding() {
    return BindingBuilder.bind(watchQueue()).to(tupleChangesExchange());
  }
}
