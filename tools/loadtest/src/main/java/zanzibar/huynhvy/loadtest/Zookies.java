package zanzibar.huynhvy.loadtest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mints Zookies the way tuple-store does, so the load test can ask for a specific consistency.
 *
 * <p>A Zookie dated in the future is always newer than any cached result, so check-service must
 * bypass its cache and evaluate against the database. That is the honest way to measure the cold
 * path with the service left exactly as it runs in production — no cache flag to flip.
 */
final class Zookies {

  private static final byte VERSION = 1;
  private static final String HMAC = "HmacSHA256";

  private Zookies() {}

  /** A Zookie no cached entry can satisfy, forcing a full evaluation. */
  static String forcingFreshRead(String secret) {
    return mint(secret, Instant.now().plus(Duration.ofHours(1)));
  }

  private static String mint(String secret, Instant commit) {
    try {
      long commitNanos = commit.getEpochSecond() * 1_000_000_000L + commit.getNano();
      ByteBuffer payload = ByteBuffer.allocate(Byte.BYTES + Long.BYTES);
      payload.put(VERSION).putLong(commitNanos);

      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
      byte[] signature = mac.doFinal(payload.array());

      ByteBuffer token = ByteBuffer.allocate(payload.array().length + signature.length);
      token.put(payload.array()).put(signature);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(token.array());
    } catch (Exception e) {
      throw new IllegalStateException("Could not mint a Zookie — check --zookie-secret", e);
    }
  }
}
