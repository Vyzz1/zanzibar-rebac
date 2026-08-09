package zanzibar.huynhvy.check.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the database clock as epoch nanos — the same clock domain as tuple commit timestamps (and
 * therefore Zookies). Used to stamp a Check result's snapshot so Zookie freshness can be compared.
 */
@Component
@RequiredArgsConstructor
public class SnapshotClock {

  private final JdbcTemplate jdbcTemplate;

  public long nowNanos() {
    Long nanos =
        jdbcTemplate.queryForObject(
            "SELECT (extract(epoch from clock_timestamp()) * 1000000000)::bigint", Long.class);
    return nanos != null ? nanos : 0L;
  }
}
