// Original source: src/main/java/carpet/patches/EntityPlayerMPFake.java
package de.gamesluk.fakeplayerapi.api;

import com.mojang.authlib.GameProfile;
import de.gamesluk.fakeplayerapi.Main;
import de.gamesluk.fakeplayerapi.integration.LuckPermsPreloadBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Stripped-down version of EntityPlayerMPFake.
 * Everything related to movement (ActionPack), riding, and special Carpet settings has been removed.
 * The most important part remains: the login behavior, which ensures the player gets loaded properly.
 */
public class EntityPlayerMPFake extends ServerPlayer {

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

        fetchGameProfile(server, gameprofile.id()).whenCompleteAsync((p, t) -> {
            try {
                spawning.remove(name);
                if (t != null) {
                    logError("[{}] profile fetch failed for name={}, id={}", requestId, name, gameprofile.id(), t);
                    return;
                }

                GameProfile current = p.name().isEmpty() ? gameprofile : p;

                LuckPermsPreloadBridge.preloadUserIfAvailable(current.id(), current.name());

                EntityPlayerMPFake instance = new EntityPlayerMPFake(server, worldIn, current, ClientInformation.createDefault());
                instance.fixStartingPosition = () -> instance.snapTo(pos.x, pos.y, pos.z, (float) yaw, (float) pitch);
                // Must be set before placeNewPlayer so that the tab list directly knows the HAT/second-layer status
                instance.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);

                // Simulate connection and register in PlayerList
                server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), instance, new CommonListenerCookie(current, 0, instance.clientInformation(), false));

                loadPlayerData(instance);

                instance.teleportTo(worldIn, pos.x, pos.y, pos.z, Set.of(), (float) yaw, (float) pitch, true);
                instance.setHealth(20.0F);
                instance.unsetRemoved();

                // Remove the player directly from the level tracker
                // so they are completely ignored by the server & other players in the world ("Superposition").
                instance.remove(RemovalReason.DISCARDED);

                instance.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6F);
                instance.gameMode.changeGameModeForPlayer(gamemode);

                server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256 / 360)), dimensionId);
                server.getPlayerList().broadcastAll(ClientboundEntityPositionSyncPacket.of(instance), dimensionId);

                instance.getAbilities().flying = flying;
                logInfo("[{}] createFake finished in {} ms", requestId, (System.nanoTime() - requestStartNanos) / 1_000_000L);
            } catch (Exception e) {
                logError("[{}] createFake async failed", requestId, e);
            }
        }, server);

        return true;
    }

    private static CompletableFuture<GameProfile> fetchGameProfile(MinecraftServer server, final UUID name) {
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

    @Override
    public void kill(ServerLevel level) {
        kill(Component.literal("Killed"));
    }

    public void kill(Component reason) {
        DisconnectionDetails details = new DisconnectionDetails(reason);
        if (reason.getContents() instanceof TranslatableContents text && text.getKey().equals("multiplayer.disconnect.duplicate_login")) {
            this.connection.onDisconnect(details);
            this.connection.disconnect(details);
        } else {
            this.level().getServer().schedule(new TickTask(this.level().getServer().getTickCount(), () -> {
                this.connection.onDisconnect(details);
                this.connection.disconnect(details);
            }));
        }
    }

    @Override
    public void tick() {
        // Ticking completely disabled (saves performance)
    }

    @Override
    public void doTick() {
        // Disabled
    }
    
    @Override
    public void baseTick() {
        // Disabled
    }
    
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

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        doCheckFallDamage(0.0, y, 0.0, onGround);
    }
}
