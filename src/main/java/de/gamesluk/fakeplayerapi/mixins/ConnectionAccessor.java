// Original source: src/main/java/carpet/mixins/Connection_packetCounterMixin.java and src/main/java/carpet/fakes/ClientConnectionInterface.java
package de.gamesluk.fakeplayerapi.mixins;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Dieses Mixin ist notwendig, damit wir in der FakeClientConnection
 * den EmbeddedChannel injizieren können, ohne eine IllegalArgumentException
 * von Netty oder dem Server zu erhalten.
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    void setChannel(Channel channel);
}
