package com.subatomicplanets.quickvoicechat.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.subatomicplanets.quickvoicechat.websocket.WebSocketManager;

public class ProximityVoiceManager {
    private final WebSocketManager webSocketManager;

    private final Map<UUID, Set<UUID>> closePlayers = new HashMap<>();

    private final double maxDistance;
    private final double minDistance;
    private final String falloffType;

    private final double connectDistance;
    private final double disconnectDistance;

    private final double maxDistSq;
    private final double minDistSq;
    private final double connectDistSq;
    private final double disconnectDistSq;

    public ProximityVoiceManager(WebSocketManager webSocketManager,
            double maxDistance,
            double minDistance,
            String falloffType) {
        this.webSocketManager = webSocketManager;

        this.maxDistance = maxDistance;
        this.minDistance = minDistance;
        this.falloffType = falloffType.toLowerCase();

        connectDistance = maxDistance + 8.0;
        disconnectDistance = maxDistance + 14.0;

        maxDistSq = maxDistance * maxDistance;
        minDistSq = minDistance * minDistance;
        connectDistSq = connectDistance * connectDistance;
        disconnectDistSq = disconnectDistance * disconnectDistance;

        webSocketManager.addOnSessionRemoved((playerId) -> {
            closePlayers.remove(playerId);
        });
    }

    public void update() {
        for (Player p1 : Bukkit.getOnlinePlayers()) {
            UUID id1 = p1.getUniqueId();
            if (!webSocketManager.playerHasWebSocket(id1))
                continue;

            Location loc1 = p1.getLocation();
            Set<UUID> currentPeers = closePlayers.computeIfAbsent(id1, k -> new HashSet<>());
            Set<UUID> newPeers = new HashSet<>();

            // Use disconnectDistance for lookup radius
            for (Entity e : p1.getNearbyEntities(disconnectDistance, disconnectDistance, disconnectDistance)) {
                if (!(e instanceof Player p2) || p2 == p1)
                    continue;

                UUID id2 = p2.getUniqueId();
                if (!webSocketManager.playerHasWebSocket(id2))
                    continue;

                double distSq = loc1.distanceSquared(p2.getLocation());
                if (distSq >= disconnectDistSq)
                    continue;

                boolean wasConnected = currentPeers.contains(id2);
                boolean inConnectRange = distSq < connectDistSq;
                if (inConnectRange || wasConnected) {
                    newPeers.add(id2);

                    // Initiate connection (lower UUID initiates)
                    if (!wasConnected && id1.compareTo(id2) < 0) {
                        webSocketManager.send(id1, "j:" + id2);
                    }

                    float volume = calculateVolume(distSq);
                    webSocketManager.send(id1, "v:" + id2 + ":" + volume);
                }
            }

            // Send disconnects
            for (UUID oldPeer : new ArrayList<>(currentPeers)) {
                if (!newPeers.contains(oldPeer)) {
                    webSocketManager.send(id1, "l:" + oldPeer);
                }
            }

            // Update tracking
            currentPeers.clear();
            currentPeers.addAll(newPeers);
        }
    }

    private float calculateVolume(double distSq) {
        if (distSq <= minDistSq)
            return 1.0f;
        if (distSq >= maxDistSq)
            return 0.0f;

        double dist = Math.sqrt(distSq);

        return switch (falloffType) {
            case "physics" -> {
                double raw = minDistance / dist;
                double cutoff = minDistance / maxDistance;
                yield (float) ((raw - cutoff) / (1.0 - cutoff));
            }
            case "game" -> {
                float rolloff = 0.5f;
                double raw = minDistance / (minDistance + rolloff * (dist - minDistance));
                double cutoff = minDistance / (minDistance + rolloff * (maxDistance - minDistance));
                yield (float) ((raw - cutoff) / (1.0 - cutoff));
            }
            default -> { // linear
                yield (float) (1.0 - (dist - minDistance) / (maxDistance - minDistance));
            }
        };
    }
}