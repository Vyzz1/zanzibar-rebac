package zanzibar.huynhvy.shared.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.security.ZookieValidator;

class ZookieValidatorTest {

  private static final String SECRET = "test-secret";
  private static final long COMMIT_NANOS = 1_752_148_800_000_000_000L;

  private final ZookieValidator validator = new ZookieValidator(SECRET);

  @Test
  void accepts_a_token_minted_with_the_same_secret() {
    assertThat(validator.validate(new Zookie(mint(SECRET, (byte) 1, COMMIT_NANOS)))).isTrue();
  }

  @Test
  void rejects_a_token_minted_with_a_different_secret() {
    assertThat(validator.validate(new Zookie(mint("other-secret", (byte) 1, COMMIT_NANOS))))
        .isFalse();
  }

  @Test
  void rejects_a_tampered_signature() {
    byte[] raw = Base64.getUrlDecoder().decode(mint(SECRET, (byte) 1, COMMIT_NANOS));
    raw[raw.length - 1] ^= 0x01; // flip a bit in the HMAC
    String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    assertThat(validator.validate(new Zookie(tampered))).isFalse();
  }

  @Test
  void rejects_an_unknown_version() {
    assertThat(validator.validate(new Zookie(mint(SECRET, (byte) 2, COMMIT_NANOS)))).isFalse();
  }

  @Test
  void rejects_garbage_and_blank_tokens() {
    assertThat(validator.validate(new Zookie("not base64 !!!"))).isFalse();
    assertThat(validator.validate(new Zookie(""))).isFalse();
    assertThat(validator.validate(new Zookie("YWJj"))).isFalse(); // valid base64, wrong length
    assertThat(validator.validate(null)).isFalse();
  }

  /** Mirrors tuple-store's ZookieMinter so the test is self-contained. */
  private static String mint(String secret, byte version, long commitNanos) {
    try {
      ByteBuffer payload = ByteBuffer.allocate(Byte.BYTES + Long.BYTES);
      payload.put(version).putLong(commitNanos);
      byte[] payloadBytes = payload.array();

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] signature = mac.doFinal(payloadBytes);

      ByteBuffer token = ByteBuffer.allocate(payloadBytes.length + signature.length);
      token.put(payloadBytes).put(signature);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(token.array());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
