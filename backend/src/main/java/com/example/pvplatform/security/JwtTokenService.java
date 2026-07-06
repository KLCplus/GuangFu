package com.example.pvplatform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {
    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(
            jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username,
                                      List<String> roles, int tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.accessTokenExpiration() * 1000);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("username", username)
            .claim("roles", roles)
            .claim("type", "access")
            .claim("v", tokenVersion)
            .issuedAt(now)
            .expiration(expiration)
            .id(UUID.randomUUID().toString())
            .signWith(signingKey)
            .compact();
    }

    public String generateRefreshToken(Long userId, int tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.refreshTokenExpiration() * 1000);

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("type", "refresh")
            .claim("v", tokenVersion)
            .issuedAt(now)
            .expiration(expiration)
            .id(UUID.randomUUID().toString())
            .signWith(signingKey)
            .compact();
    }

    /**
     * Parse an access token (backward-compatible).
     */
    public TokenClaims parseToken(String token) {
        return parseToken(token, "access");
    }

    /**
     * Parse a token with expected type validation.
     */
    public TokenClaims parseToken(String token, String expectedType) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String type = claims.get("type", String.class);
        if (!expectedType.equals(type)) {
            throw new JwtException("expected token type " + expectedType + " but got " + type);
        }

        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        Integer tokenVersion = claims.get("v", Integer.class);

        return new TokenClaims(userId, username, roles, tokenVersion, type);
    }

    public long getAccessExpirationSeconds() {
        return jwtProperties.accessTokenExpiration();
    }

    public long getRefreshExpirationSeconds() {
        return jwtProperties.refreshTokenExpiration();
    }
}
