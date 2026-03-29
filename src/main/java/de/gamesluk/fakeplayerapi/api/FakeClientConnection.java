// Original source: src/main/java/carpet/patches/FakeClientConnection.java
package de.gamesluk.fakeplayerapi.api;

import de.gamesluk.fakeplayerapi.Main;
import de.gamesluk.fakeplayerapi.mixins.ConnectionAccessor;
import org.jetbrains.annotations.Nullable;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeClientConnection extends Connection {
    private static final Logger LOGGER = LoggerFactory.getLogger(FakeClientConnection.class);

    public FakeClientConnection(PacketFlow p) {
        super(p);
        // Wir brauchen hier in unserer Implementation das Mixin (ConnectionAccessor), um den Channel zu setzen!
        ((ConnectionAccessor)this).setChannel(new EmbeddedChannel());
        logDebug("FakeClientConnection initialisiert; flow={}", p);
    }

    @Override
    public void setReadOnly() {
        // No-op wie in Carpet; kein reguläres Logging.
    }

    @Override
    public boolean isMemoryConnection() {
        return true;
    }
    
    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl) {
        // Bewusst unterdrueckt: Fake-Verbindung verschluckt ausgehende Pakete.
    }

    @Override
    public void handleDisconnection() {
        // No-op.
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener packetListener) {
        // No-op.
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetListener) {
        // No-op.
    }

    private static void logDebug(String message, Object... args) {
        if (Main.isDebugEnabled()) {
            LOGGER.info("[FakePlayerDebug][Connection] " + message, args);
        }
    }
}
