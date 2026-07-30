package com.subatomicplanets.quickvoicechat.token;

import java.time.Instant;
import java.util.UUID;

public record ConnectionToken(
                UUID playerId,
                String token,
                String ipAddress,
                Instant expiry) {
}