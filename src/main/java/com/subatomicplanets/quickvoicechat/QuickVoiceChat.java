package com.subatomicplanets.quickvoicechat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.subatomicplanets.quickvoicechat.token.TokenManager;
import com.subatomicplanets.quickvoicechat.voice.ProximityVoiceManager;
import com.subatomicplanets.quickvoicechat.webserver.WebServerManager;
import com.subatomicplanets.quickvoicechat.websocket.WebSocketManager;

public final class QuickVoiceChat extends JavaPlugin implements Listener {
    private TokenManager tokenManager;
    private WebServerManager webServerManager;
    private WebSocketManager webSocketManager;
    private ProximityVoiceManager proximityVoiceManager;

    @Override
    public void onEnable() {
        getLogger().info("QuickVoiceChat plugin enabled!");

        // Config setup
        saveDefaultConfig();

        // Regsiter event listener
        Bukkit.getPluginManager().registerEvents(this, this);

        // Get config for managers
        int webPort = getConfig().getInt("webserver.port", 25588);
        double maxDistance = getConfig().getDouble("voice.max-distance", 30.0);
        double minDistance = getConfig().getDouble("voice.min-distance", 1.0);
        String falloffType = getConfig().getString("voice.falloff-type", "game");

        // Create managers
        tokenManager = new TokenManager();
        webSocketManager = new WebSocketManager(this);
        webServerManager = new WebServerManager(this, tokenManager, webSocketManager, webPort);
        proximityVoiceManager = new ProximityVoiceManager(webSocketManager, maxDistance, minDistance, falloffType);

        // Connection messages
        if (getConfig().getBoolean("chat.connection-messages", true)) {
            webSocketManager.addOnSessionAdded((playerId) -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(Component.text("Voice Chat connected!")
                            .color(NamedTextColor.GREEN));
                }
            });

            webSocketManager.addOnSessionRemoved((playerId) -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(Component.text("Voice Chat disconnected!")
                            .color(NamedTextColor.RED));
                }
            });
        }

        // Start web server
        try {
            webServerManager.start();
        } catch (Exception e) {
            getLogger().severe("Failed to start web server: " + e.getMessage());
        }

        // Start proximity voice chat manager task
        Bukkit.getScheduler().runTaskTimer(this, proximityVoiceManager::update, 20L, 5L);

        // Register voice chat command
        PluginCommand command = getCommand("vc");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof org.bukkit.entity.Player player) {
                    sendVoiceChatLink(player);
                } else {
                    sender.sendMessage("Only players can use this command!");
                }
                return true;
            });
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("QuickVoiceChat plugin disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("chat.auto-send", true)) {
            sendVoiceChatLink(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        webSocketManager.removeSession(playerId);
        tokenManager.removePlayerTokens(playerId);
    }

    private void sendVoiceChatLink(org.bukkit.entity.Player player) {
        UUID playerId = player.getUniqueId();
        webSocketManager.removeSession(playerId);
        tokenManager.removePlayerTokens(playerId);

        // Just in case
        if (player.getAddress() == null) {
            return;
        }

        // Generate and send new session token
        String token = tokenManager.generateToken(playerId, player.getAddress().getAddress().getHostAddress());
        if (token != null) {
            String address = getConfig().getString("webserver.address", "127.0.0.1");
            int port = getConfig().getInt("webserver.port", 25588);
            String link = address + ":" + port + "/vc?t=" + token;
            player.sendMessage(Component.text("Click here to join the voice chat!")
                    .color(NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.openUrl(link)));
        } else {
            player.sendMessage(Component.text("Could not generate voice chat link!")
                    .color(NamedTextColor.RED));
        }
    }
}