package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.domain.ZookieMinter;

class ZookieMinterTest {

  private static final OffsetDateTime TS =
      OffsetDateTime.of(2026, 7, 10, 12, 0, 0, 0, ZoneOffset.UTC);

  private final ZookieMinter minter = new ZookieMinter("test-secret");

  @Test
  void mints_token_with_version_timestamp_and_hmac_layout() {
    Zookie zookie = minter.mint(TS);

    byte[] raw = Base64.getUrlDecoder().decode(zookie.token());
    // 1 version byte + 8 timestamp bytes + 32 HMAC-SHA256 bytes
    assertThat(raw).hasSize(1 + 8 + 32);
    assertThat(raw[0]).isEqualTo((byte) 1);
  }

  @Test
  void is_deterministic_for_same_timestamp_and_secret() {
    assertThat(minter.mint(TS).token()).isEqualTo(minter.mint(TS).token());
  }

  @Test
  void different_timestamps_produce_different_tokens() {
    assertThat(minter.mint(TS).token()).isNotEqualTo(minter.mint(TS.plusNanos(1)).token());
  }

  @Test
  void different_secret_yields_different_signature() {
    String a = new ZookieMinter("secret-a").mint(TS).token();
    String b = new ZookieMinter("secret-b").mint(TS).token();
    assertThat(a).isNotEqualTo(b);
  }
}
