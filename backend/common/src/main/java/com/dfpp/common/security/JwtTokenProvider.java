package com.dfpp.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT provider shared by the gateway (validation) and the
 * upload service (issuance). HMAC-SHA256 signed, with subject = username,
 * a {@code uid} claim for the numeric user id and a {@code roles} claim.
 */
public class JwtTokenProvider {

    private final SecretKey key;
    private final long validityMillis;

    public JwtTokenProvider(String secret, long validityMillis) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = validityMillis;
    }

    public String generateToken(long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMillis);
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public AuthenticatedUser toUser(String token) {
        Claims c = parse(token);
        Object rawRoles = c.get("roles");
        List<String> roles = rawRoles instanceof List
                ? ((List<?>) rawRoles).stream().map(String::valueOf).toList()
                : List.of("ROLE_USER");
        long uid = ((Number) c.getOrDefault("uid", 0)).longValue();
        return new AuthenticatedUser(uid, c.getSubject(), roles);
    }

    public Map<String, Object> claimsAsMap(String token) {
        return parse(token);
    }
}
