package com.subatomicplanets.quickvoicechat.webserver;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.subatomicplanets.quickvoicechat.token.TokenManager;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;

public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final TokenManager tokenManager;

    public HttpRequestHandler(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        // Double check IP
        String ip = getRemoteIp(ctx);
        if (!tokenManager.hasIpRequestedToken(ip)) {
            ctx.close();
            return;
        }

        // Only allow valid paths
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        String path = decoder.path();
        if (!"/vc".equals(path) && !"/vcws".equals(path)) {
            ctx.close();
            return;
        }

        // Get token
        List<String> tokens = decoder.parameters().get("t");
        if (tokens == null || tokens.isEmpty()) {
            ctx.close();
            return;
        }

        // Only allow valid token
        String token = tokens.get(0);
        if (!tokenManager.validateToken(token, ip)) {
            ctx.close();
            return;
        }

        // Handle websocket vs http request
        UUID playerId = tokenManager.getPlayerFromToken(token);
        if ("/vcws".equals(path)) {
            ctx.channel().attr(AttributeKey.<UUID>valueOf("playerId")).set(playerId);
            req.retain();
            ctx.fireChannelRead(req);
            ctx.pipeline().remove(this);
        } else {
            sendVoiceChatPage(ctx, playerId);
        }
    }

    private void sendVoiceChatPage(ChannelHandlerContext ctx, UUID playerId) {
        String htmlTemplate = readResourceToString("web/index.html");
        String javascript = readResourceToString("web/script.js");
        String stylesheet = readResourceToString("web/style.css");

        String completeHtml = htmlTemplate
                .replace("<link rel=\"stylesheet\" href=\"/style.css\">", "<style>\n" + stylesheet + "\n</style>")
                .replace("<script src=\"/script.js\"></script>", "<script>\n" + javascript + "\n</script>");

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(completeHtml.getBytes(StandardCharsets.UTF_8)));

        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8")
                .set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String getRemoteIp(ChannelHandlerContext ctx) {
        SocketAddress addr = ctx.channel().remoteAddress();
        if (addr instanceof InetSocketAddress inet) {
            return inet.getAddress().getHostAddress();
        }
        return "";
    }

    private String readResourceToString(String resourcePath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null)
                return "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "";
        }
    }
}