// Original source: src/main/java/carpet/commands/PlayerCommand.java and src/main/java/carpet/patches/EntityPlayerMPFake.java
package de.gamesluk.fakeplayerapi.api;

import net.minecraft.network.chat.Component;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Kern-API Klasse zum einfachen Spawnen und Verwalten von Fake Playern.
 * Basiert auf der Logik aus PlayerCommand.java.
 */
public class FakePlayerAPI {

    /**
     * Spawnt einen neuen Fake Player an einer unsichtbaren Standard-Position 
     * (Positionsdaten und Gamemode sind nicht mehr relevant, da der Spieler in einer Superposition existiert und nicht getickt wird).
     * 
     * @param server Der Minecraft Server.
     * @param username Der Name des Spielers (online oder offline).
     * @return true, falls das Spawnen initiiert wurde, false wenn der Name ungültig ist.
     */
    public static boolean spawnFakePlayer(MinecraftServer server, String username) {
        PlayerList manager = server.getPlayerList();

        // 1. Prüfungen (Ist er schon da oder loggt gerade ein?)
        if (EntityPlayerMPFake.isSpawningPlayer(username)) {
            // Spieler lädt bereits
            return false;
        }
        if (manager.getPlayerByName(username) != null) {
            // Spieler ist bereits online
            return false;
        }

        // 2. UUID und GameProfile herausfinden (Online/Offline Mode Support)
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, username);
        if (uuid == null) {
            // Offline-Mode: Fallback auf Offline UUID
            server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(username);
        }
        
        if (uuid == null) {
            return false; // Keine UUID generierbar
        }

        // 3. Fake Player erzeugen (delegate an die Core-Klasse)
        // Hardcodierte Werte für Position, Dimension und Gamemode.
        return EntityPlayerMPFake.createFake(username, server, Vec3.ZERO, 0, 0, Level.OVERWORLD, GameType.SURVIVAL, false);
    }

    /**
     * Entfernt einen bereits gespawnten Fake Player per Name.
     *
     * @param server Der Minecraft Server.
     * @param username Der zu entfernende Spielername.
     * @return true, wenn ein Fake Player gefunden und entfernt wurde.
     */
    public static boolean deleteFakePlayer(MinecraftServer server, String username) {
        if (EntityPlayerMPFake.isSpawningPlayer(username)) {
            return false;
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (!(player instanceof EntityPlayerMPFake fakePlayer)) {
            return false;
        }

        fakePlayer.kill(Component.literal("Fake-Spieler entfernt."));
        return true;
    }

    /**
     * Prüft, ob ein gegebener Spieler ein Fake Player ist.
     *
     * @param server Der Minecraft Server.
     * @param username des zu überprüfende Spielers
     * @return true, wenn es sich um einen Fake Player handelt, sonst false
     */
    public static boolean isFake(MinecraftServer server, String username) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        return player instanceof EntityPlayerMPFake;
    }
}
