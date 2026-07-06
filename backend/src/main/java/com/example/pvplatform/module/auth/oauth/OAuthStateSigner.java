package com.example.pvplatform.module.auth.oauth;

import com.example.pvplatform.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Stateless HMAC-signed OAuth state tokens.
 * State is a pipe-delimited string: provider|redirectUri|expirySeconds|nonce|hmac
 * where hmac covers the first 4 parts.
 * Short TTL mitigates replay (for strict anti-replay, a denylist would be needed — future work).
 */
@Component
public class OAuthStateSigner {
    private static final int TTL_SECONDS = 600; // 10 minutes
    private static final String DELIMITER = "|";
    private static final String HMAC_ALG = "HmacSHA256";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public OAuthStateSigner(com.example.pvplatform.security.JwtProperties jwtProperties) {
        this.key = new SecretKeySpec(
            jwtProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALG);
    }

    public String sign(String provider, String redirectUri) {
        long exp = Instant.now().getEpochSecond() + TTL_SECONDS;
        String nonce = Long.toHexString(random.nextLong());
        String payload = provider + DELIMITER + redirectUri + DELIMITER + exp + DELIMITER + nonce;
        String hmac = hmac(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            (payload + DELIMITER + hmac).getBytes(StandardCharsets.UTF_8));
    }

    public ParsedState verify(String stateToken, String expectedProvider) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(stateToken), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "非法的 OAuth state");
        }

        String[] parts = decoded.split("\\" + DELIMITER);
        if (parts.length != 5) {
            throw new BusinessException(400, "非法的 OAuth state");
        }

        String provider = parts[0];
        String redirectUri = parts[1];
        long exp;
        try {
            exp = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "非法的 OAuth state");
        }
        String nonce = parts[3];
        String hmac = parts[4];

        String payload = provider + DELIMITER + redirectUri + DELIMITER + exp + DELIMITER + nonce;
        if (!hmac(payload).equals(hmac)) {
            throw new BusinessException(400, "OAuth state 签名验证失败");
        }
        if (Instant.now().getEpochSecond() > exp) {
            throw new BusinessException(400, "OAuth state 已过期");
        }
        if (!provider.equals(expectedProvider)) {
            throw new BusinessException(400, "OAuth provider 不匹配");
        }

        return new ParsedState(provider, redirectUri, nonce);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(key);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 签名失败", e);
        }
    }

    public record ParsedState(String provider, String redirectUri, String nonce) {
    }
}
