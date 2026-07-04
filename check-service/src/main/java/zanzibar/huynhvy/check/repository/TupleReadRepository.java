package zanzibar.huynhvy.check.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zanzibar.huynhvy.shared.domain.RelationTuple;

public interface TupleReadRepository extends JpaRepository<RelationTuple, String> {}
