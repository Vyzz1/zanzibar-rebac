package zanzibar.huynhvy.check.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zanzibar.huynhvy.shared.domain.RelationTuple;

/**
 * Runs several checks in one call. Each is independent, so they execute concurrently on virtual
 * threads; results are returned in request order. If any check fails (e.g. an invalid Zookie), the
 * whole batch fails with that error.
 */
@Service
@RequiredArgsConstructor
public class BatchCheckUseCase {

  private final CheckUseCase checkUseCase;

  /** One check within a batch: the tuple to evaluate plus its optional Zookie. */
  public record BatchItem(RelationTuple tuple, String zookie) {}

  public List<Boolean> checkAll(List<BatchItem> items) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Boolean>> futures =
          items.stream()
              .map(item -> executor.submit(() -> checkUseCase.check(item.tuple(), item.zookie())))
              .toList();

      List<Boolean> results = new ArrayList<>(futures.size());
      for (Future<Boolean> future : futures) {
        results.add(join(future));
      }
      return results;
    }
  }

  private static boolean join(Future<Boolean> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted during batch check", e);
    } catch (ExecutionException e) {
      // Surface the original failure (e.g. IllegalArgumentException for an invalid Zookie).
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("Batch check failed", e.getCause());
    }
  }
}
