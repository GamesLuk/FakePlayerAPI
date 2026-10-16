// Original source: src/main/java/carpet/patches/EntityPlayerMPFake.java
package de.gamesluk.fakeplayerapi.api;

import com.mojang.authlib.GameProfile;
import de.gamesluk.fakeplayerapi.Main;
import de.gamesluk.fakeplayerapi.integration.LuckPermsPreloadBridge;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Stripped-down version of EntityPlayerMPFake.
 * Everything related to movement (ActionPack), riding, and special Carpet settings has been removed.
 * The most important part remains: the login behavior, which ensures the player gets loaded properly.
 */
public class EntityPlayerMPFake extends ServerPlayer {

    private static final byte ALL_LAYERS = (byte) Arrays.stream(PlayerModelPart.values()).mapToInt(PlayerModelPart::getMask).reduce(0, (a, b) -> a | b);

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityPlayerMPFake.class);
    private static final String DEBUG_PREFIX = "[FakePlayerDebug]";
    private static final Set<String> spawning = new HashSet<>();
    public Runnable fixStartingPosition = () -> {};

    public static boolean createFake(String username, MinecraftServer server, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying) {
        final long requestStartNanos = System.nanoTime();
        final String requestId = username + "#" + Long.toUnsignedString(requestStartNanos, 36);

        logInfo("[{}] createFake start; username={}, pos={}, dim={}, gamemode={}, flying={}",
                requestId, username, pos, dimensionId, gamemode, flying);

        ServerLevel worldIn = server.getLevel(dimensionId);
        
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, username);

        if (uuid == null) {
            // Always allow offline spawning by default
            server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(username);
        }

        GameProfile gameprofile = new GameProfile(uuid, username);
        String name = gameprofile.name();
        spawning.add(name);

        fetchGameProfile(server, gameprofile.name()).whenCompleteAsync((p, t) -> {

            spawning.remove(name);

            if (t != null) {
                logError("[{}] profile fetch failed for name={}, id={}", requestId, name, gameprofile.id(), t);
                return;
            }

            GameProfile current = p.name().isEmpty() ? gameprofile : p;

            LuckPermsPreloadBridge.preloadUserIfAvailable(current.id(), current.name());

            EntityPlayerMPFake instance = new EntityPlayerMPFake(server, worldIn, current, ClientInformation.createDefault());
            instance.fixStartingPosition = () -> instance.snapTo(pos.x, pos.y, pos.z, (float) yaw, (float) pitch);

            // Simulate connection and register in PlayerList
            server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), instance, new CommonListenerCookie(current, 0, instance.clientInformation(), false));

            loadPlayerData(instance);

            instance.stopRiding();
            instance.teleportTo(worldIn, pos.x, pos.y, pos.z, Set.of(), (float) yaw, (float) pitch, true);
            instance.setHealth(20.0F);
            instance.unsetRemoved();
            instance.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6F);
            instance.gameMode.changeGameModeForPlayer(gamemode);

            // Remove the player directly from the level tracker
            // so they are completely ignored by the server and other players in the world ("Superposition").
            instance.remove(RemovalReason.DISCARDED);

            server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256 / 360)), dimensionId);
            server.getPlayerList().broadcastAll(ClientboundEntityPositionSyncPacket.of(instance), dimensionId);

            instance.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_LAYERS);

            // Update the hat-layer visibility in the tab list (Minecraft 26.3)
            server.getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT,
                            instance
                    )
            );

            instance.getAbilities().flying = flying;

            logInfo("[{}] createFake finished in {} ms", requestId, (System.nanoTime() - requestStartNanos) / 1_000_000L);

        }, server);
        return true;
    }

    private static CompletableFuture<GameProfile> fetchGameProfile(MinecraftServer server, final String name) {
        final ResolvableProfile resolvableProfile = ResolvableProfile.createUnresolved(name);
        return resolvableProfile.resolveProfile(server.services().profileResolver());
    }

    private static void loadPlayerData(EntityPlayerMPFake player) {
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(player.problemPath(), LOGGER)) {
            Optional<ValueInput> optional = player.level().getServer().getPlayerList().loadPlayerData(player.nameAndId())
                    .map(compoundTag -> TagValueInput.create(scopedCollector, player.registryAccess(), compoundTag));
            optional.ifPresent(valueInput -> {
                player.load(valueInput);
                player.loadAndSpawnEnderPearls(valueInput);
                player.loadAndSpawnParentVehicle(valueInput);
            });
        }
    }

    private static void logInfo(String message, Object... args) {
        if (Main.isDebugEnabled()) {
            LOGGER.info(DEBUG_PREFIX + " " + message, args);
        }
    }

    private static void logError(String message, Object... args) {
        if (Main.isDebugEnabled()) {
            LOGGER.error(DEBUG_PREFIX + " " + message, args);
        }
    }

    public static EntityPlayerMPFake respawnFake(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation cli) {
        return new EntityPlayerMPFake(server, level, profile, cli);
    }

    public static boolean isSpawningPlayer(String username) {
        return spawning.contains(username);
    }

    private EntityPlayerMPFake(MinecraftServer server, ServerLevel worldIn, GameProfile profile, ClientInformation cli) {
        super(server, worldIn, profile, cli);
    }

    @Override
    public void onEquipItem(final EquipmentSlot slot, final ItemStack previous, final ItemStack stack) {
        if (!isUsingItem()) super.onEquipItem(slot, previous, stack);
    }

    public void kill(Component reason) {

        if (reason.getContents() instanceof TranslatableContents text && text.getKey().equals("multiplayer.disconnect.duplicate_login")) {
            this.connection.onDisconnect(new DisconnectionDetails(reason));
        } else {
            this.level().getServer().schedule(new TickTask(this.level().getServer().getTickCount(), () -> {
                this.connection.onDisconnect(new DisconnectionDetails(reason));
            }));
        }
    }

    @Override
    public void tick() {}

    @Override
    public void doTick() {}
    
    @Override
    public void baseTick() {}
    
    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        setHealth(20);
        this.foodData = new FoodData();
        kill(this.getCombatTracker().getDeathMessage());
    }

    @Override
    public String getIpAddress() {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsListing() {
        // IMPORTANT: Return true so the player shows up in the tab menu!
        return true; 
    }
}
