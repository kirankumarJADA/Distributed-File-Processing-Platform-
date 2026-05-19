package com.dfpp.gateway;

import com.dfpp.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider =
            new JwtTokenProvider("test-secret-key-test-secret-key-1234567890", 60_000);

    @Test
    void generatesAndValidatesToken() {
        String token = provider.generateToken(42L, "alice", List.of("ROLE_USER"));
        assertTrue(provider.isValid(token));
        assertEquals("alice", provider.toUser(token).username());
        assertEquals(42L, provider.toUser(token).userId());
        assertTrue(provider.toUser(token).hasRole("USER"));
    }

    @Test
    void rejectsTamperedToken() {
        String token = provider.generateToken(1L, "bob", List.of("ROLE_USER"));
        assertFalse(provider.isValid(token + "tampered"));
    }
}
