package com.subatomicplanets.quickvoicechat.webserver;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.subatomicplanets.quickvoicechat.token.TokenManager;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class IpFilterHandler extends ChannelInboundHandlerAdapter {

    private final TokenManager tokenManager;

    public IpFilterHandler(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String ip = getRemoteIp(ctx);

        if (!tokenManager.hasIpRequestedToken(ip)) {
            ctx.close();
            return;
        }

        ctx.fireChannelActive();
    }

    private String getRemoteIp(ChannelHandlerContext ctx) {
        SocketAddress addr = ctx.channel().remoteAddress();
        if (addr instanceof InetSocketAddress inet) {
            return inet.getAddress().getHostAddress();
        }
        return "";
    }
}