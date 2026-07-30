package com.subatomicplanets.quickvoicechat.webserver;

import java.io.File;

import com.subatomicplanets.quickvoicechat.QuickVoiceChat;
import com.subatomicplanets.quickvoicechat.token.TokenManager;
import com.subatomicplanets.quickvoicechat.websocket.WebSocketFrameHandler;
import com.subatomicplanets.quickvoicechat.websocket.WebSocketManager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public class WebServerManager {
    private final QuickVoiceChat plugin;
    private final TokenManager tokenManager;
    private final WebSocketManager webSocketManager;
    private final int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public WebServerManager(QuickVoiceChat plugin,
            TokenManager tokenManager,
            WebSocketManager webSocketManager,
            int port) {
        this.plugin = plugin;
        this.tokenManager = tokenManager;
        this.webSocketManager = webSocketManager;
        this.port = port;
    }

    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        SslContext sslContext = loadSslContext();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new IpFilterHandler(tokenManager));

                        if (sslContext != null) {
                            pipeline.addLast(sslContext.newHandler(ch.alloc()));
                        }

                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new HttpRequestHandler(tokenManager));
                        pipeline.addLast(new WebSocketServerProtocolHandler(
                                WebSocketServerProtocolConfig.newBuilder()
                                        .websocketPath("/vcws")
                                        .checkStartsWith(true)
                                        .maxFramePayloadLength(65536)
                                        .build()));
                        pipeline.addLast(new WebSocketFrameHandler(webSocketManager));
                    }
                });

        serverChannel = bootstrap.bind(port).sync().channel();
        plugin.getLogger().info("QuickVoiceChat web server started on port " + port +
                (sslContext != null ? " (HTTPS)" : " (HTTP)"));
    }

    private SslContext loadSslContext() {
        try {
            File dataFolder = plugin.getDataFolder();
            File certFile = new File(dataFolder, "cert.pem");
            File keyFile = new File(dataFolder, "key.pem");

            if (certFile.exists() && keyFile.exists()) {
                return SslContextBuilder.forServer(certFile, keyFile).build();
            } else {
                plugin.getLogger().warning("SSL certificates not found in plugin data folder! Using HTTP!");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load SSL certificates: " + e.getMessage());
        }
        return null;
    }

    public void stop() {
        if (serverChannel != null)
            serverChannel.close();
        if (bossGroup != null)
            bossGroup.shutdownGracefully();
        if (workerGroup != null)
            workerGroup.shutdownGracefully();
    }
}