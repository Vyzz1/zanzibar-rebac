package zanzibar.huynhvy.loadtest;

import java.util.Arrays;

/**
 * Latency percentiles for one scenario. Percentiles are taken from every recorded sample rather
 * than a running average, because the tail is what matters for an authorization check sitting on
 * someone else's request path.
 */
public record Stats(String scenario, long[] latenciesNanos, long errors, long elapsedNanos) {

  public Stats {
    Arrays.sort(latenciesNanos);
  }

  public long count() {
    return latenciesNanos.length;
  }

  public double throughputPerSecond() {
    return elapsedNanos == 0 ? 0 : count() / (elapsedNanos / 1_000_000_000.0);
  }

  public double percentileMillis(double percentile) {
    if (latenciesNanos.length == 0) {
      return 0;
    }
    int index = (int) Math.ceil(percentile / 100.0 * latenciesNanos.length) - 1;
    return latenciesNanos[Math.clamp(index, 0, latenciesNanos.length - 1)] / 1_000_000.0;
  }

  /** One aligned row, so runs can be eyeballed side by side. */
  public String toRow() {
    return String.format(
        "%-18s %9d %12.0f %9.2f %9.2f %9.2f %9.2f %7d",
        scenario,
        count(),
        throughputPerSecond(),
        percentileMillis(50),
        percentileMillis(95),
        percentileMillis(99),
        percentileMillis(100),
        errors);
  }

  public static String header() {
    return String.format(
        "%-18s %9s %12s %9s %9s %9s %9s %7s",
        "scenario", "calls", "req/s", "p50 ms", "p95 ms", "p99 ms", "max ms", "errors");
  }
}
