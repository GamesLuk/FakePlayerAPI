package de.gamesluk.fakeplayerapi.api;

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

public class PlayerConnectionHadler implements ServerConfigurationConnectionEvents.Configure {

    @Override
    public void onSendConfiguration(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {

        // Du kannst zum Beispiel auf das Gameprofile zugreifen:
        String playerName = handler.getOwner().name(); // Get name from GameProfile during config
        System.out.println("[DEBUG] Authentifiziert, aber noch nicht in der Welt: " + playerName);

        FakePlayerAPI.deleteFakePlayer(server, playerName);
    }
}
