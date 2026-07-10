package zanzibar.huynhvy.check.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Read-only JPA mapping of the {@code tuplestore.relation_tuples} table, which is owned and written
 * by tuple-store. check-service only ever reads through this entity.
 */
@Entity
@Table(name = "relation_tuples")
public class RelationTupleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String namespace;

  @Column(name = "object_id", nullable = false)
  private String objectId;

  @Column(nullable = false)
  private String relation;

  @Column(name = "subject_id", nullable = false)
  private String subjectId;

  @Column(name = "commit_timestamp", nullable = false)
  private OffsetDateTime commitTimestamp;

  protected RelationTupleEntity() {
    // required by JPA
  }

  public Long getId() {
    return id;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getObjectId() {
    return objectId;
  }

  public String getRelation() {
    return relation;
  }

  public String getSubjectId() {
    return subjectId;
  }

  public OffsetDateTime getCommitTimestamp() {
    return commitTimestamp;
  }
}
