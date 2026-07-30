package com.subatomicplanets.quickvoicechat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.subatomicplanets.quickvoicechat.token.TokenManager;

public class TokenManagerTest {
    private TokenManager tokenManager;

    @BeforeEach
    public void setUp() {
        tokenManager = new TokenManager();
    }

    @ParameterizedTest
    @ValueSource(strings = { "127.0.0.1", "192.168.1.50", "test", "" })
    public void testHasIpRequestedToken(String testIp) {
        boolean result = tokenManager.hasIpRequestedToken(testIp);
        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "127.0.0.1", "192.168.1.50", "test", "" })
    public void testGenerateToken(String testIp) {
        tokenManager.generateToken(UUID.randomUUID(), testIp);
        boolean result = tokenManager.hasIpRequestedToken(testIp);
        assertTrue(result);
    }

    @Test
    public void testValidateTokenNegative() {
        boolean result = tokenManager.validateToken("test", "127.0.0.1");
        assertFalse(result);
    }

    @Test
    public void testValidateTokenPositive() {
        String token = tokenManager.generateToken(UUID.randomUUID(), "127.0.0.1");
        boolean result = tokenManager.validateToken(token, "127.0.0.1");
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "123456789", "test", "" })
    public void testGetPlayerFromTokenNegative(String testToken) {
        UUID result = tokenManager.getPlayerFromToken(testToken);
        assertNull(result);
    }

    @Test
    public void testGetPlayerFromTokenPositive() {
        String token = tokenManager.generateToken(UUID.randomUUID(), "127.0.0.1");
        UUID result = tokenManager.getPlayerFromToken(token);
        assertNotNull(result);
    }
}