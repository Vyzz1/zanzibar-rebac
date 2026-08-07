package zanzibar.huynhvy.check.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TupleReadRepository extends JpaRepository<RelationTupleEntity, Long> {

  boolean existsByNamespaceAndObjectIdAndRelationAndSubjectId(
      String namespace, String objectId, String relation, String subjectId);

  /**
   * Subjects of {@code relation} on the given object — the targets a {@code TupleToUserset} follows
   * (e.g. the parent objects reached via a "parent" relation).
   */
  @Query(
      "select t.subjectId from RelationTupleEntity t"
          + " where t.namespace = ?1 and t.objectId = ?2 and t.relation = ?3")
  List<String> findSubjectIdsByNamespaceAndObjectIdAndRelation(
      String namespace, String objectId, String relation);
}
