package com.subatomicplanets.quickvoicechat.websocket;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import com.subatomicplanets.quickvoicechat.QuickVoiceChat;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;

public class WebSocketManager {
    private final QuickVoiceChat plugin;

    private final Map<UUID, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    private final Set<Consumer<UUID>> sessionAddedCallbacks = new CopyOnWriteArraySet<>();
    private final Set<Consumer<UUID>> sessionRemovedCallbacks = new CopyOnWriteArraySet<>();

    public WebSocketManager(QuickVoiceChat plugin) {
        this.plugin = plugin;
    }

    public void addSession(UUID playerId, Channel channel) {
        if (playerId == null || channel == null)
            return;

        WebSocketSession session = activeSessions.put(playerId, new WebSocketSession(playerId, channel));
        if (session != null && session.channel().isOpen()) {
            session.channel().close();
        }

        plugin.getLogger().info("VoiceChat WebSocket connected");
        sessionAddedCallbacks.forEach(callback -> callback.accept(playerId));
    }

    public void addOnSessionAdded(Consumer<UUID> callback) {
        if (callback != null) {
            sessionAddedCallbacks.add(callback);
        }
    }

    public void removeSession(UUID playerId) {
        if (playerId == null)
            return;

        WebSocketSession session = activeSessions.remove(playerId);
        if (session != null) {
            if (session.channel().isOpen()) {
                session.channel().close();
            }

            plugin.getLogger().info("VoiceChat WebSocket disconnected");
            sessionRemovedCallbacks.forEach(callback -> callback.accept(playerId));
        }
    }

    public void addOnSessionRemoved(Consumer<UUID> callback) {
        if (callback != null) {
            sessionRemovedCallbacks.add(callback);
        }
    }

    public void processCommand(UUID senderId, String data) {
        if (senderId == null || data == null)
            return;

        String[] parts = data.split(":", 3);
        if (parts.length < 3) {
            return;
        }

        String cmd = parts[0];
        String targetStr = parts[1];
        String payload = parts[2];

        // Only forward offer, answer, and candidate
        if (!("o".equals(cmd) || "a".equals(cmd) || "c".equals(cmd))) {
            return;
        }

        UUID targetId;
        try {
            targetId = UUID.fromString(targetStr);
        } catch (Exception e) {
            return;
        }

        String forwarded = cmd + ":" + senderId + ":" + payload;
        send(targetId, forwarded);
    }

    public void send(UUID playerId, String message) {
        if (playerId == null || !activeSessions.containsKey(playerId))
            return;

        TextWebSocketFrame frame = new TextWebSocketFrame(message);
        WebSocketSession session = activeSessions.get(playerId);
        if (session.channel().isOpen()) {
            session.channel().writeAndFlush(frame.retainedDuplicate());
        }
        frame.release();
    }

    public boolean playerHasWebSocket(UUID playerId) {
        if (playerId == null)
            return false;

        if (activeSessions.containsKey(playerId)) {
            WebSocketSession session = activeSessions.get(playerId);
            if (session == null || !session.channel().isOpen()) {
                activeSessions.remove(playerId);
                return false;
            }
            return true;
        }
        return false;
    }
}