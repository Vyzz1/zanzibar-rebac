package zanzibar.huynhvy.check.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TupleReadRepository extends JpaRepository<RelationTupleEntity, Long> {

  boolean existsByNamespaceAndObjectIdAndRelationAndSubjectId(
      String namespace, String objectId, String relation, String subjectId);
}
