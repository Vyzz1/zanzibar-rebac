package zanzibar.huynhvy.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.shared.domain.Zookie;

/**
 * Verifies the HMAC of a Zookie minted by tuple-store's {@code ZookieMinter}. Token layout: {@code
 * base64url(version(1) | commit_ts_nanos(8) | HMAC-SHA256(secret, payload))}. The secret must match
 * the one used to mint.
 */
@Component
public class ZookieValidator {

  private static final byte VERSION = 1;
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int PAYLOAD_LENGTH = Byte.BYTES + Long.BYTES; // version + timestamp
  private static final int SIGNATURE_LENGTH = 32; // HMAC-SHA256 output
  private static final int TOKEN_LENGTH = PAYLOAD_LENGTH + SIGNATURE_LENGTH;

  private final byte[] secret;

  public ZookieValidator(@Value("${zookie.secret}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  /** Returns true only if the token is well-formed and its HMAC matches; false otherwise. */
  public boolean validate(Zookie zookie) {
    if (zookie == null || zookie.token() == null || zookie.token().isBlank()) {
      return false;
    }

    byte[] raw;
    try {
      raw = Base64.getUrlDecoder().decode(zookie.token());
    } catch (IllegalArgumentException e) {
      return false;
    }

    if (raw.length != TOKEN_LENGTH || raw[0] != VERSION) {
      return false;
    }

    byte[] payload = Arrays.copyOfRange(raw, 0, PAYLOAD_LENGTH);
    byte[] signature = Arrays.copyOfRange(raw, PAYLOAD_LENGTH, TOKEN_LENGTH);
    return MessageDigest.isEqual(sign(payload), signature); // constant-time comparison
  }

  private byte[] sign(byte[] data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(data);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to compute Zookie HMAC", e);
    }
  }
}
