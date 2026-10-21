package kr.junhyung.mcagents.botfabric.mixin;

import kr.junhyung.mcagents.botfabric.session.Disconnects;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What the server said as it refused the login: a whitelist, a ban, a full server. The login has a
 * listener of its own, which the common one's hook never sees.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientHandshakePacketListenerMixin {

    @Inject(method = "handleDisconnect", at = @At("HEAD"))
    private void botfabric$told(ClientboundLoginDisconnectPacket packet, CallbackInfo info) {
        Disconnects.told(packet.reason().getString());
    }
}
