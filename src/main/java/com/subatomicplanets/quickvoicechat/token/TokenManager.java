package com.subatomicplanets.quickvoicechat.token;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TokenManager {
    private final Map<String, ConnectionToken> activeTokens = new ConcurrentHashMap<>();

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    public String generateToken(UUID playerId, String ipAddress) {
        if (playerId == null || ipAddress == null)
            return null;

        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        String token = base64Encoder.encodeToString(randomBytes);

        ConnectionToken ct = new ConnectionToken(
                playerId,
                token,
                ipAddress,
                Instant.now().plusSeconds(120));

        activeTokens.put(token, ct);
        return token;
    }

    public boolean validateToken(String token, String requestIp) {
        // Remove old tokens first
        removeOldTokens();
        if (token == null || requestIp == null)
            return false;

        ConnectionToken ct = activeTokens.get(token);
        if (ct == null || ct.expiry().isBefore(Instant.now())) {
            return false;
        }

        return ct.ipAddress().equals(requestIp);
    }

    public boolean hasIpRequestedToken(String ip) {
        // Remove old tokens first
        removeOldTokens();
        if (ip == null)
            return false;

        for (ConnectionToken ct : activeTokens.values()) {
            if (ip.equals(ct.ipAddress())) {
                return true;
            }
        }
        return false;
    }

    public UUID getPlayerFromToken(String token) {
        // Remove old tokens first
        removeOldTokens();
        if (token == null)
            return null;

        if (activeTokens.containsKey(token)) {
            return activeTokens.get(token).playerId();
        }
        return null;
    }

    public void removePlayerTokens(UUID playerId) {
        if (playerId == null)
            return;

        activeTokens.entrySet().removeIf(e -> e.getValue().playerId().equals(playerId));
    }

    public void removeOldTokens() {
        Instant now = Instant.now();
        activeTokens.entrySet().removeIf(e -> e.getValue().expiry().isBefore(now));
    }
}