package me.adrianed.authmevelocity.velocity.listener;

import java.util.Optional;

import org.slf4j.Logger;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import me.adrianed.authmevelocity.velocity.AuthMeVelocityPlugin;
import me.adrianed.authmevelocity.velocity.utils.AuthmeUtils;

public class ConnectListener {
    private final ProxyServer proxy;
    private final Logger logger;
    private final AuthMeVelocityPlugin plugin;
    
    public ConnectListener(AuthMeVelocityPlugin plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.proxy = proxy;
    }

    @Subscribe(order = PostOrder.LATE)
    public void onInitialServer(PlayerChooseInitialServerEvent event, Continuation continuation){
        if(!plugin.config().get().ensureAuthServer().ensureFirstServerIsAuthServer()) {
            continuation.resume();
            plugin.logDebug("PlayerChooseInitialServerEvent | Not enabled");
            return;
        }

        Optional<RegisteredServer> optionalSV = event.getInitialServer();
        if(optionalSV.isPresent() && plugin.isAuthServer(optionalSV.get())){
            continuation.resume();
            plugin.logDebug("PlayerChooseInitialServerEvent | Player is in auth server");
            return;
        }
        var config = plugin.config().get();
        var server = AuthmeUtils.serverToSend(
            config.ensureAuthServer().sendMode(), proxy, config.authServers(), config.randomAttempts());

        // Velocity takes over in case the initial server is not present
        event.setInitialServer(server.object());
        continuation.resume();
        if (server.isEmpty()) {
            plugin.logDebug("PlayerChooseInitialServerEvent | Null server");
            logger.error("Cannot send the player {} to an auth server", event.getPlayer().getUsername());
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event, Continuation continuation) {
        if (!event.getResult().isAllowed() || plugin.isLogged(event.getPlayer())) {
            plugin.logDebug("ServerPreConnectEvent | Not allowed or player not logged");
            continuation.resume();
            return;
        }

        // this should be present, "event.getResult().isAllowed()" is the "isPresent" check
        if(!plugin.isAuthServer(event.getResult().getServer().orElse(null))) {
            plugin.logDebug("ServerPreConnectEvent | Server is not an auth server");
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        }
        continuation.resume();
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        final Player player = event.getPlayer();
        if (plugin.isLogged(player) && plugin.isInAuthServer(player)) {
            plugin.logDebug("ServerPostConnectEvent | Already logged player and connected to an Auth server");
            final ByteArrayDataOutput buf = ByteStreams.newDataOutput();
            buf.writeUTF("LOGIN");
            player.getCurrentServer().ifPresent(sv ->
                sv.sendPluginMessage(AuthMeVelocityPlugin.AUTHMEVELOCITY_CHANNEL, buf.toByteArray()));
        }
    }
}
