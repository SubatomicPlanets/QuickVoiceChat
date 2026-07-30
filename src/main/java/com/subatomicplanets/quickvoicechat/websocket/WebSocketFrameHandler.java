package com.subatomicplanets.quickvoicechat.websocket;

import java.util.UUID;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final WebSocketManager wsManager;
    private UUID playerId;

    public WebSocketFrameHandler(WebSocketManager wsManager) {
        this.wsManager = wsManager;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            playerId = ctx.channel().attr(AttributeKey.<UUID>valueOf("playerId")).get();
            if (playerId != null) {
                wsManager.addSession(playerId, ctx.channel());
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            if (playerId != null) {
                String textData = ((TextWebSocketFrame) frame).text();
                wsManager.processCommand(playerId, textData);
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (playerId != null) {
            wsManager.removeSession(playerId);
        }
    }
}