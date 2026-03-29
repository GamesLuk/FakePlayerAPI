// Original source: src/main/java/carpet/mixins/PlayerList_fakePlayersMixin.java
package de.gamesluk.fakeplayerapi.mixins;

import com.mojang.authlib.GameProfile;
import de.gamesluk.fakeplayerapi.api.EntityPlayerMPFake;
import de.gamesluk.fakeplayerapi.api.NetHandlerPlayServerFake;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Dieses Mixin hookt die Fake Player in die Standard-Minecraft-Netzwerk- und Login-Pfäde ein.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;"))
    private void fixStartingPos(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
        if (serverPlayer instanceof EntityPlayerMPFake) {
            ((EntityPlayerMPFake) serverPlayer).fixStartingPosition.run();
        }
    }

    @WrapOperation(method = "placeNewPlayer", at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"))
    private ServerGamePacketListenerImpl replaceNetworkHandler(MinecraftServer server, Connection clientConnection, ServerPlayer playerIn, CommonListenerCookie cookie, Operation<ServerGamePacketListenerImpl> original) {
        if (playerIn instanceof EntityPlayerMPFake fake) {
            return new NetHandlerPlayServerFake(this.server, clientConnection, fake, cookie);
        } else {
            return original.call(server, clientConnection, playerIn, cookie);
        }
    }

    @WrapOperation(method = "respawn", at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;"))
    public ServerPlayer makePlayerForRespawn(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation cli, Operation<ServerPlayer> original, ServerPlayer serverPlayer, boolean i) {
        if (serverPlayer instanceof EntityPlayerMPFake) {
            return EntityPlayerMPFake.respawnFake(minecraftServer, serverLevel, gameProfile, cli);
        }
        return original.call(minecraftServer, serverLevel, gameProfile, cli);
    }

    @WrapOperation(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
            )
    )
    private void suppressFakeJoinMessage(PlayerList playerList, Component component, boolean overlay, Operation<Void> original, Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie) {
        if (!(serverPlayer instanceof EntityPlayerMPFake)) {
            original.call(playerList, component, overlay);
        }
    }
}
