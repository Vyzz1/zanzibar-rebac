package zanzibar.huynhvy.namespace.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NamespaceOutboxRepository extends JpaRepository<NamespaceOutboxEvent, Long> {

  /**
   * Fetches a batch of unpublished events, locking the rows so that concurrent poller instances
   * skip already-claimed rows ({@code FOR UPDATE SKIP LOCKED}). Must run inside a transaction.
   */
  @Query(
      value =
          "SELECT * FROM outbox_events WHERE published = false ORDER BY id LIMIT :limit"
              + " FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<NamespaceOutboxEvent> findUnpublishedForUpdate(@Param("limit") int limit);
}
