package zanzibar.huynhvy.tuplestore.repository;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "relation_tuples")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RelationTupleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  @Column(name = "namespace", nullable = false)
  private String namespace;

  @Column(name = "object_id", nullable = false)
  private String objectId;

  @Column(name = "relation", nullable = false)
  private String relation;

  @Column(name = "subject_id", nullable = false)
  private String subjectId;

  @ColumnDefault("clock_timestamp()")
  @Column(name = "commit_timestamp", nullable = false)
  private OffsetDateTime commitTimestamp;
}
