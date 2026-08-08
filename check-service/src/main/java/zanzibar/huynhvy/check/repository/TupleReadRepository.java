package zanzibar.huynhvy.check.repository;

import java.util.List;
import org.springframework.data.domain.Limit;
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

  /**
   * Subjects of {@code relation} on the given object that are themselves usersets (contain a {@code
   * '#'}, e.g. {@code group:eng#member}). GraphTraverser expands each so an indirect member is
   * granted the relation too.
   */
  @Query(
      "select t.subjectId from RelationTupleEntity t"
          + " where t.namespace = ?1 and t.objectId = ?2 and t.relation = ?3"
          + " and t.subjectId like '%#%'")
  List<String> findUsersetSubjectIds(String namespace, String objectId, String relation);

  /**
   * Lists stored tuples in a namespace, narrowed by any of the optional filters (a {@code null}
   * filter matches anything). Used by the Read API; capped by {@code limit}.
   */
  @Query(
      "select t from RelationTupleEntity t where t.namespace = :namespace"
          + " and (:objectId is null or t.objectId = :objectId)"
          + " and (:relation is null or t.relation = :relation)"
          + " and (:subjectId is null or t.subjectId = :subjectId)"
          + " and (:afterId is null or t.id > :afterId)"
          + " order by t.id")
  List<RelationTupleEntity> findByFilter(
      String namespace,
      String objectId,
      String relation,
      String subjectId,
      Long afterId,
      Limit limit);
}
