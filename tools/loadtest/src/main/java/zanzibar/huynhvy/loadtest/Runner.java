package zanzibar.huynhvy.loadtest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives one scenario from a fixed number of virtual threads, each looping as fast as it can until
 * the deadline — a closed-loop load test, so the reported throughput is what this client could pull
 * at that concurrency, not an offered rate.
 *
 * <p>Every scenario is warmed up first and those samples are thrown away; on the JVM the first
 * seconds measure the interpreter and JIT, not the system under test.
 */
final class Runner {

  private Runner() {}

  static Stats measure(String scenario, Config config, Runnable call) {
    loop(config.concurrency(), seconds(config.warmupSeconds()), call);
    Result result = loop(config.concurrency(), seconds(config.durationSeconds()), call);
    return new Stats(scenario, result.latencies(), result.errors(), result.elapsedNanos());
  }

  private static Result loop(int concurrency, long durationNanos, Runnable call) {
    long deadline = System.nanoTime() + durationNanos;
    AtomicLong errors = new AtomicLong();
    long start = System.nanoTime();

    List<long[]> perWorker = new ArrayList<>(concurrency);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<long[]>> futures = new ArrayList<>(concurrency);
      for (int i = 0; i < concurrency; i++) {
        futures.add(executor.submit(() -> work(deadline, call, errors)));
      }
      for (Future<long[]> future : futures) {
        perWorker.add(join(future));
      }
    }

    return new Result(merge(perWorker), errors.get(), System.nanoTime() - start);
  }

  private static long[] work(long deadline, Runnable call, AtomicLong errors) {
    long[] samples = new long[1024];
    int size = 0;
    while (System.nanoTime() < deadline) {
      long began = System.nanoTime();
      try {
        call.run();
      } catch (RuntimeException e) {
        errors.incrementAndGet();
        continue; // a failed call has no meaningful latency
      }
      if (size == samples.length) {
        samples = Arrays.copyOf(samples, size * 2);
      }
      samples[size++] = System.nanoTime() - began;
    }
    return Arrays.copyOf(samples, size);
  }

  private static long[] merge(List<long[]> perWorker) {
    int total = perWorker.stream().mapToInt(a -> a.length).sum();
    long[] all = new long[total];
    int offset = 0;
    for (long[] samples : perWorker) {
      System.arraycopy(samples, 0, all, offset, samples.length);
      offset += samples.length;
    }
    return all;
  }

  private static long[] join(Future<long[]> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while running the load test", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("A load-test worker failed", e.getCause());
    }
  }

  private static long seconds(int value) {
    return value * 1_000_000_000L;
  }

  private record Result(long[] latencies, long errors, long elapsedNanos) {}
}
