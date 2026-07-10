package zanzibar.huynhvy.tuplestore.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.shared.domain.Zookie;

@Component
public class ZookieMinter {

  private static final byte VERSION = 1;
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final byte[] secret;

  public ZookieMinter(@Value("${zookie.secret}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Mints a signed token: {@code base64url(version | commit_ts_nanos | HMAC-SHA256(secret,
   * payload))}. The HMAC lets any consumer detect a forged or tampered Zookie.
   */
  public Zookie mint(OffsetDateTime commitTimestamp) {
    long commitNanos =
        commitTimestamp.toInstant().getEpochSecond() * 1_000_000_000L
            + commitTimestamp.toInstant().getNano();

    ByteBuffer payload = ByteBuffer.allocate(Byte.BYTES + Long.BYTES);
    payload.put(VERSION).putLong(commitNanos);
    byte[] payloadBytes = payload.array();

    byte[] signature = sign(payloadBytes);
    ByteBuffer token = ByteBuffer.allocate(payloadBytes.length + signature.length);
    token.put(payloadBytes).put(signature);

    return new Zookie(Base64.getUrlEncoder().withoutPadding().encodeToString(token.array()));
  }

  private byte[] sign(byte[] data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(data);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to sign Zookie", e);
    }
  }
}
