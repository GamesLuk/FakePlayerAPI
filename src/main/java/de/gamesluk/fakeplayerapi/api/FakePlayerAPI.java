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
 * Core API class for easily spawning and managing fake players.
 * Based on logic from PlayerCommand.java.
 */
public class FakePlayerAPI {

    /**
     * Spawns a new fake player at an invisible default position
     * (Positional data and gamemode are irrelevant, as the player exists in a superposition and is not being ticked).
     * 
     * @param server The Minecraft Server.
     * @param username The name of the player (online or offline).
     * @return true if the spawning process gets initiated, false if the name is already taken or invalid.
     */
    public static boolean spawnFakePlayer(MinecraftServer server, String username) {
        PlayerList manager = server.getPlayerList();

        // 1. Checks (Is the player already there or currently logging in?)
        if (EntityPlayerMPFake.isSpawningPlayer(username)) {
            // Player is already loading
            return false;
        }
        if (manager.getPlayerByName(username) != null) {
            // Player is already online
            return false;
        }

        // 2. Resolve UUID and GameProfile (Online/Offline Mode Support)
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, username);
        if (uuid == null) {
            // Offline-Mode: Fallback to an offline UUID
            server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(username);
        }
        
        if (uuid == null) {
            return false; // Could not generate a UUID
        }

        // 3. Create the fake player (delegate to the core class)
        // Hardcoded values for position, dimension, and gamemode.
        return EntityPlayerMPFake.createFake(username, server, Vec3.ZERO, 0, 0, Level.OVERWORLD, GameType.SURVIVAL, false);
    }

    /**
     * Removes an already spawned fake player by name.
     *
     * @param server The Minecraft Server.
     * @param username The name of the player to remove.
     * @return true if a fake player was found and successfully removed.
     */
    public static boolean deleteFakePlayer(MinecraftServer server, String username) {
        if (EntityPlayerMPFake.isSpawningPlayer(username)) {
            return false;
        }

        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (!(player instanceof EntityPlayerMPFake fakePlayer)) {
            return false;
        }

        fakePlayer.kill(Component.literal("Fake player removed."));
        return true;
    }

    /**
     * Checks whether a given player is a fake player.
     *
     * @param server The Minecraft Server.
     * @param username The name of the player to check.
     * @return true if it is a fake player, false otherwise.
     */
    public static boolean isFake(MinecraftServer server, String username) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        return player instanceof EntityPlayerMPFake;
    }
}
