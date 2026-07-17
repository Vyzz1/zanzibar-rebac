package zanzibar.huynhvy.tuplestore.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

  /**
   * Fetches a batch of unpublished events, locking the rows so that concurrent poller instances
   * skip already-claimed rows ({@code FOR UPDATE SKIP LOCKED}). Must run inside a transaction.
   */
  @Query(
      value =
          "SELECT * FROM outbox_events WHERE published = false ORDER BY id LIMIT :limit"
              + " FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> findUnpublishedForUpdate(@Param("limit") int limit);
}
