package com.glyart.authmevelocity.proxy.listener;

import com.glyart.authmevelocity.proxy.AuthMeVelocityPlugin;
import com.glyart.authmevelocity.proxy.event.ProxyLoginEvent;
import com.google.common.io.ByteArrayDataInput;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
//import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Optional;
import java.util.UUID;

public class ProxyListener {
    private final AuthMeVelocityPlugin plugin;
    private final ProxyServer server;

    public ProxyListener(AuthMeVelocityPlugin plugin, ProxyServer server) {
        this.plugin = plugin;
        this.server = server;
    }

    @Subscribe
    public void onPluginMessage(final PluginMessageEvent event) {
        if (!(event.getSource() instanceof ServerConnection)) return;

        if (!event.getIdentifier().getId().equals("authmevelocity:main")) return;

        ByteArrayDataInput input = event.dataAsDataStream();
        String sChannel = input.readUTF();
        if (!sChannel.equals("LOGIN")) return;

        String user = input.readUTF();
        Optional<Player> player = server.getPlayer(UUID.fromString(user));
        if (!player.isPresent()) return;

        Player loggedPlayer = player.get();
        UUID playerUUID = loggedPlayer.getUniqueId();
        if (!plugin.loggedPlayers.contains(playerUUID)){
            plugin.loggedPlayers.add(playerUUID);

            RegisteredServer loginServer = player.get().getCurrentServer().get().getServer();
            server.getEventManager().fireAndForget(new ProxyLoginEvent(loggedPlayer, loginServer));
        }
    }

    @Subscribe
    public void onDisconnect(final DisconnectEvent event) {
        plugin.loggedPlayers.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onCommandExecute(final CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) return;

        if (plugin.loggedPlayers.contains(player.getUniqueId())) return;

        Optional<ServerConnection> server = player.getCurrentServer();
        boolean isAuthServer = server.isPresent() &&
            AuthMeVelocityPlugin.getConfig().getList("authservers").contains(server.get().getServerInfo().getName());

        if (isAuthServer) {
            event.setResult(CommandExecuteEvent.CommandResult.forwardToServer());
        } else {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }

    @Subscribe
    public void onPlayerChat(final PlayerChatEvent event) {
        final Player player = event.getPlayer();
        if (plugin.loggedPlayers.contains(player.getUniqueId())) return;

        Optional<ServerConnection> server = player.getCurrentServer();
        if (server.isPresent() && AuthMeVelocityPlugin.getConfig().getList("authservers").contains(server.get().getServerInfo().getName())) {
            return;
        }

        event.setResult(PlayerChatEvent.ChatResult.denied());
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        final Player player = event.getPlayer();
        if (plugin.loggedPlayers.contains(player.getUniqueId())) return;

        Optional<RegisteredServer> server = event.getResult().getServer();
        if (server.isPresent() && AuthMeVelocityPlugin.getConfig().getList("authservers").contains(server.get().getServerInfo().getName())) {
            return;
        }

        event.setResult(ServerPreConnectEvent.ServerResult.denied());
    }

    /*
    "You have the opportunity to modify the response sent to the remote player."
    In theory... it could be modified, but the respective methods do not exist.
    I hope that the other event works for <1.12 even though this one should work.
    */

    /*@Subscribe
    public void onTabComplete(TabCompleteEvent event){
        Player player = event.getPlayer();
        if (plugin.loggedPlayers.contains(player.getUniqueId())) return;
        event.setTabComplete();?
    }*/

    @Subscribe
    public void onTabComplete(PlayerAvailableCommandsEvent event){
        Player player = event.getPlayer();
        if (!plugin.loggedPlayers.contains(player.getUniqueId())) {
            event.getRootNode().getChildren().iterator().remove();
        }
    }
}
