package kr.junhyung.mcagents.botfabric.mixin;

import kr.junhyung.mcagents.botfabric.event.Feeds;
import kr.junhyung.mcagents.botfabric.event.ResourcePacks;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notices what the client concluded about the server's resource pack.
 *
 * <p>There is no event for it and no field to read afterwards: the client decides, tells the server
 * in a packet, and forgets. Watching what goes out is therefore the only way to know, and it is the
 * client's own verdict rather than a guess from a log line.
 *
 * <p>On the way out of {@code send}, which every packet passes through. One {@code instanceof} per
 * packet is nothing next to writing one.
 *
 * <p>The two dialog packets are here as well, because they are common packets and this is the
 * listener that handles them. Read from the packet rather than from the screen: the dialog feed is
 * about what the server sent, and a reader that waited for a screen would be asking whether the
 * client happened to have drawn it yet.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerMixin {

    @Inject(method = "handleShowDialog", at = @At("TAIL"))
    private void botfabric$showDialog(ClientboundShowDialogPacket packet, CallbackInfo info) {
        Feeds.dialog(packet.dialog().value());
    }

    @Inject(method = "handleClearDialog", at = @At("TAIL"))
    private void botfabric$clearDialog(ClientboundClearDialogPacket packet, CallbackInfo info) {
        Feeds.dialogClosed();
    }

    @Inject(method = "send", at = @At("HEAD"))
    private void botfabric$resourcePack(Packet<?> packet, CallbackInfo info) {
        if (packet instanceof ServerboundResourcePackPacket pack) {
            ResourcePacks.reported(pack.action().name());
        }
    }
}
