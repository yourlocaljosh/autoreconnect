package klism.dev.autoreconnect.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public class AutoReconnectClient implements ClientModInitializer {

    private static final int FIRST_ATTEMPT_DELAY_TICKS = 3 * 20;
    private static final int RETRY_DELAY_TICKS         = 60 * 20;

    private ServerData lastServer = null;
    private boolean awaitingResult = false;
    private int countdown = -1;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerData current = client.getCurrentServer();
            if (current != null) {
                lastServer = current;
            }
            awaitingResult = false;
            countdown = -1;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        if (mc.screen instanceof DisconnectedScreen && lastServer != null) {
            if (countdown < 0) {
                countdown = awaitingResult ? RETRY_DELAY_TICKS : FIRST_ATTEMPT_DELAY_TICKS;
            } else if (--countdown <= 0) {
                reconnect(mc);
            }
        } else if (mc.screen instanceof ConnectScreen) {
            //
        } else {
            boolean inGame = mc.level != null;
            boolean atMenu = mc.screen instanceof TitleScreen
                    || mc.screen instanceof JoinMultiplayerScreen;
            if (inGame || atMenu) {
                countdown = -1;
                awaitingResult = false;
            }
        }
    }

    private void reconnect(Minecraft mc) {
        awaitingResult = true; //if this fails, the next pass schedules the 60s back-off
        countdown = -1;

        ServerAddress address = ServerAddress.parseString(lastServer.ip);
        JoinMultiplayerScreen parent = new JoinMultiplayerScreen(new TitleScreen());
        ConnectScreen.startConnecting(parent, mc, address, lastServer, false, null);
    }
}