package zanzibar.huynhvy.loadtest;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.BatchCheckRequest;
import zanzibar.huynhvy.api.CheckRequest;
import zanzibar.huynhvy.shared.auth.AuthClientInterceptor;

/**
 * Measures the check path against a running system.
 *
 * <p>The scenarios are paired on purpose: the same question is asked with and without a Zookie, so
 * the difference between the two rows is the price of insisting on a fresh read — and everything
 * the cache is buying. The derived and group rows then show what recursion over the relation graph
 * costs on top of a direct lookup.
 *
 * <p>Numbers are only as good as the machine they run on; the header records that so a result is
 * never quoted without its context.
 */
public final class LoadTest {

  private LoadTest() {}

  public static void main(String[] args) throws Exception {
    Config config;
    try {
      config = Config.parse(args);
    } catch (RuntimeException e) {
      System.out.println(e.getMessage());
      System.out.println();
      System.out.println(Config.usage());
      return;
    }

    ManagedChannel checkChannel = channel(config.checkHost(), config.checkPort(), config.token());
    ManagedChannel tupleChannel = channel(config.tupleHost(), config.tuplePort(), config.token());
    try {
      var checkStub = AuthorizationServiceGrpc.newBlockingStub(checkChannel);
      var writeStub = AuthorizationServiceGrpc.newBlockingStub(tupleChannel);

      System.out.println("Seeding fixtures...");
      new Seeder(config, writeStub).seed();

      String freshZookie = Zookies.forcingFreshRead(config.zookieSecret());
      List<Stats> results = new ArrayList<>();

      // Cached: the steady-state hot path, served from Redis.
      results.add(
          Runner.measure(
              "direct-cached",
              config,
              () -> checkStub.check(check(config.namespace(), "viewer", "user:bob", ""))));

      // Same question, but the Zookie makes the cache unusable — full evaluation every call.
      results.add(
          Runner.measure(
              "direct-fresh",
              config,
              () -> checkStub.check(check(config.namespace(), "viewer", "user:bob", freshZookie))));

      // Derived: viewer is reached through union(this, computedUserset(editor)).
      results.add(
          Runner.measure(
              "derived-fresh",
              config,
              () ->
                  checkStub.check(check(config.namespace(), "viewer", "user:alice", freshZookie))));

      // Group: the grant is to group:eng#member, so the subject is found by expanding a userset.
      results.add(
          Runner.measure(
              "group-fresh",
              config,
              () ->
                  checkStub.check(
                      check(config.groupNamespace(), "viewer", "user:carol", freshZookie))));

      // Ten questions in one round trip, against the same cached answer.
      BatchCheckRequest batch = batchOf(config, 10);
      results.add(Runner.measure("batch10-cached", config, () -> checkStub.batchCheck(batch)));

      report(config, results);
    } catch (IOException | StatusRuntimeException e) {
      // Connection failures often carry no message, so name the type rather than printing "null".
      String cause = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      System.out.println();
      System.out.println("Could not reach the services: " + cause);
      System.out.println(
          "Start the infrastructure and all three services first."
              + " See tools/loadtest/README.md.");
    } finally {
      shutdown(checkChannel);
      shutdown(tupleChannel);
    }
  }

  private static CheckRequest check(
      String namespace, String relation, String subjectId, String zookie) {
    return CheckRequest.newBuilder()
        .setNamespace(namespace)
        .setObjectId("report.pdf")
        .setRelation(relation)
        .setSubjectId(subjectId)
        .setZookie(zookie)
        .build();
  }

  private static BatchCheckRequest batchOf(Config config, int size) {
    BatchCheckRequest.Builder builder = BatchCheckRequest.newBuilder();
    for (int i = 0; i < size; i++) {
      builder.addChecks(check(config.namespace(), "viewer", "user:bob", ""));
    }
    return builder.build();
  }

  private static ManagedChannel channel(String host, int port, String token) {
    return NettyChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .intercept(new AuthClientInterceptor(token))
        .build();
  }

  private static void shutdown(ManagedChannel channel) throws InterruptedException {
    channel.shutdownNow();
    channel.awaitTermination(5, TimeUnit.SECONDS);
  }

  private static void report(Config config, List<Stats> results) {
    Runtime runtime = Runtime.getRuntime();
    System.out.println();
    System.out.printf(
        "concurrency=%d  duration=%ds  warmup=%ds  cpus=%d  java=%s  os=%s%n",
        config.concurrency(),
        config.durationSeconds(),
        config.warmupSeconds(),
        runtime.availableProcessors(),
        System.getProperty("java.version"),
        System.getProperty("os.name"));
    System.out.println(
        "Closed-loop client on the same host as the services."
            + " Treat these as relative, not as production capacity.");
    System.out.println();
    System.out.println(Stats.header());
    results.forEach(stats -> System.out.println(stats.toRow()));
  }
}
