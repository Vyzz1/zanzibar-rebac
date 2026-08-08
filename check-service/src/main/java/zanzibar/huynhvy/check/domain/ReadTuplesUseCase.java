package zanzibar.huynhvy.check.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import zanzibar.huynhvy.check.repository.RelationTupleEntity;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.RelationTuple;

/**
 * Lists stored tuples matching a filter — the Read side of the API. Returns raw tuples with no
 * userset rewrite evaluation (that is {@link CheckUseCase}'s job). A namespace is required to keep
 * the query bounded; the other filters are optional. Pages are cursored on the row id.
 */
@Service
@RequiredArgsConstructor
public class ReadTuplesUseCase {

  private static final int DEFAULT_PAGE_SIZE = 100;
  private static final int MAX_PAGE_SIZE = 1000;

  private final TupleReadRepository repository;

  public ReadTuplesResult read(
      String namespace,
      String objectId,
      String relation,
      String subjectId,
      int pageSize,
      String pageToken) {
    if (isBlank(namespace)) {
      throw new IllegalArgumentException("namespace is required");
    }

    int size = clampPageSize(pageSize);
    Long afterId = decodeToken(pageToken);

    // Fetch one extra row to learn whether a further page exists without a second query.
    List<RelationTupleEntity> rows =
        repository.findByFilter(
            namespace,
            blankToNull(objectId),
            blankToNull(relation),
            blankToNull(subjectId),
            afterId,
            Limit.of(size + 1));

    boolean hasMore = rows.size() > size;
    List<RelationTupleEntity> page = hasMore ? rows.subList(0, size) : rows;
    String nextPageToken = hasMore ? encodeToken(page.get(page.size() - 1).getId()) : "";

    return new ReadTuplesResult(
        page.stream().map(ReadTuplesUseCase::toDomain).toList(), nextPageToken);
  }

  private static int clampPageSize(int pageSize) {
    if (pageSize <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }

  private static String encodeToken(long id) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
  }

  private static Long decodeToken(String pageToken) {
    if (isBlank(pageToken)) {
      return null;
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(pageToken);
      return Long.parseLong(new String(decoded, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid page_token", e);
    }
  }

  private static RelationTuple toDomain(RelationTupleEntity entity) {
    return new RelationTuple(
        entity.getNamespace(), entity.getObjectId(), entity.getRelation(), entity.getSubjectId());
  }

  private static String blankToNull(String value) {
    return isBlank(value) ? null : value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
