package zanzibar.huynhvy.shared.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Counts rejected calls as {@code
 * zanzibar.auth.rejected{reason=unauthenticated|insufficient_scope}} — a spike in either is the
 * signal that a caller is misconfigured or probing.
 */
@Component
public class AuthFailureRecorder {

  private static final String METRIC = "zanzibar.auth.rejected";

  private final MeterRegistry registry;
  private final ConcurrentMap<String, Counter> byReason = new ConcurrentHashMap<>();

  public AuthFailureRecorder(MeterRegistry registry) {
    this.registry = registry;
  }

  public void record(String reason) {
    byReason
        .computeIfAbsent(
            reason,
            key ->
                Counter.builder(METRIC)
                    .description("API calls rejected by authentication or scope checks")
                    .tag("reason", key)
                    .register(registry))
        .increment();
  }
}
