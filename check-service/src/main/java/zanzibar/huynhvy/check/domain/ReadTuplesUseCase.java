package zanzibar.huynhvy.check.domain;

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
 * the query bounded; the other filters are optional.
 */
@Service
@RequiredArgsConstructor
public class ReadTuplesUseCase {

  private static final int DEFAULT_PAGE_SIZE = 100;
  private static final int MAX_PAGE_SIZE = 1000;

  private final TupleReadRepository repository;

  public List<RelationTuple> read(
      String namespace, String objectId, String relation, String subjectId, int pageSize) {
    if (isBlank(namespace)) {
      throw new IllegalArgumentException("namespace is required");
    }

    return repository
        .findByFilter(
            namespace,
            blankToNull(objectId),
            blankToNull(relation),
            blankToNull(subjectId),
            Limit.of(clampPageSize(pageSize)))
        .stream()
        .map(ReadTuplesUseCase::toDomain)
        .toList();
  }

  private static int clampPageSize(int pageSize) {
    if (pageSize <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
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
