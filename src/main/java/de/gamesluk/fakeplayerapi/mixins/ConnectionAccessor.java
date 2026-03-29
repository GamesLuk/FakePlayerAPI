// Original source: src/main/java/carpet/mixins/Connection_packetCounterMixin.java and src/main/java/carpet/fakes/ClientConnectionInterface.java
package de.gamesluk.fakeplayerapi.mixins;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * This mixin is required so we can inject the EmbeddedChannel
 * into the FakeClientConnection without throwing an 
 * IllegalArgumentException from Netty or the server.
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    void setChannel(Channel channel);
}
