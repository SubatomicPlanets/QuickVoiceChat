package com.subatomicplanets.quickvoicechat.websocket;

import java.util.UUID;
import io.netty.channel.Channel;

public record WebSocketSession(
        UUID playerId,
        Channel channel) {
}