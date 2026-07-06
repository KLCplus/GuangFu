package com.example.pvplatform.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {
    private static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-256-bits-long-so-we-need-more-chars-here";
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, 7200L, 604800L);
        jwtTokenService = new JwtTokenService(props);
    }

    @Test
    void shouldGenerateAndParseAccessToken() {
        String token = jwtTokenService.generateAccessToken(1L, "demo", List.of("USER"), 0);
        TokenClaims claims = jwtTokenService.parseToken(token);

        assertEquals(1L, claims.userId());
        assertEquals("demo", claims.username());
        assertEquals(List.of("USER"), claims.roles());
        assertEquals("access", claims.type());
        assertEquals(0, claims.tokenVersion());
    }

    @Test
    void shouldGenerateAndParseRefreshToken() {
        String token = jwtTokenService.generateRefreshToken(1L, 3);
        TokenClaims claims = jwtTokenService.parseToken(token, "refresh");

        assertEquals(1L, claims.userId());
        assertEquals("refresh", claims.type());
        assertEquals(3, claims.tokenVersion());
    }

    @Test
    void shouldRejectRefreshTokenAsAccessToken() {
        String refreshToken = jwtTokenService.generateRefreshToken(1L, 0);
        assertThrows(JwtException.class, () -> jwtTokenService.parseToken(refreshToken));
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        JwtProperties shortProps = new JwtProperties(SECRET, 0L, 604800L);
        JwtTokenService shortService = new JwtTokenService(shortProps);
        String token = shortService.generateAccessToken(1L, "demo", List.of("USER"), 0);

        assertThrows(ExpiredJwtException.class, () -> shortService.parseToken(token));
    }

    @Test
    void shouldRejectWrongSignature() {
        String token = jwtTokenService.generateAccessToken(1L, "demo", List.of("USER"), 0);

        JwtProperties otherProps = new JwtProperties("other-" + SECRET, 7200L, 604800L);
        JwtTokenService otherService = new JwtTokenService(otherProps);

        assertThrows(JwtException.class, () -> otherService.parseToken(token));
    }

    @Test
    void shouldReturnExpirationSeconds() {
        assertEquals(7200L, jwtTokenService.getAccessExpirationSeconds());
        assertEquals(604800L, jwtTokenService.getRefreshExpirationSeconds());
    }

    @Test
    void shouldContainCorrectClaims() {
        String token = jwtTokenService.generateAccessToken(99L, "admin", List.of("ADMIN", "USER"), 0);
        TokenClaims claims = jwtTokenService.parseToken(token);

        assertEquals(99L, claims.userId());
        assertEquals("admin", claims.username());
        assertTrue(claims.roles().contains("ADMIN"));
        assertTrue(claims.roles().contains("USER"));
    }
}
