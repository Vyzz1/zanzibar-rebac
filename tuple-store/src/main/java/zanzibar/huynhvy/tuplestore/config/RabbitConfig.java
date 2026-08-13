package zanzibar.huynhvy.tuplestore.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * tuple-store publishes tuple changes to a fanout exchange so every consumer (watch-service,
 * check-service, …) can bind its own queue and receive all events — rather than competing for a
 * single shared queue.
 */
@Configuration
public class RabbitConfig {

  public static final String TUPLE_CHANGES_EXCHANGE = "tuple-changes";

  @Bean
  public FanoutExchange tupleChangesExchange() {
    return ExchangeBuilder.fanoutExchange(TUPLE_CHANGES_EXCHANGE).durable(true).build();
  }
}
