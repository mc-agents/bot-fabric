package kr.junhyung.mcagents.botfabric.mixin;

import kr.junhyung.mcagents.botfabric.event.Feeds;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The five feeds Fabric API does not offer an event for.
 *
 * <p>Chat arrives through {@code ClientReceiveMessageEvents}. An action bar, a title, a subtitle,
 * a sound and a particle do not, and a mineflayer bot reports all five, so without these the same
 * world reads as silent and titleless depending on which kind of bot is looking at it.
 *
 * <p>The action bar is here as well as in the GAME event, because they are different packets:
 * {@code /title actionbar} sends this one, and the GAME overlay flag covers the other route.
 *
 * <p>Read from the packet rather than from the screen: a title is drawn for a few seconds and then
 * gone, and a reader that waited for the HUD would be asking whether it happened to still be up.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "setActionBarText", at = @At("TAIL"))
    private void botfabric$actionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo info) {
        Feeds.actionBar(packet.text());
    }

    @Inject(method = "setTitleText", at = @At("TAIL"))
    private void botfabric$title(ClientboundSetTitleTextPacket packet, CallbackInfo info) {
        Feeds.title("title", packet.text());
    }

    @Inject(method = "setSubtitleText", at = @At("TAIL"))
    private void botfabric$subtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo info) {
        Feeds.title("subtitle", packet.text());
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void botfabric$sound(ClientboundSoundPacket packet, CallbackInfo info) {
        Feeds.effect("sound", packet.getSound().value().location().toString());
    }

    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void botfabric$particle(ClientboundLevelParticlesPacket packet, CallbackInfo info) {
        Feeds.effect("particle",
                BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString());
    }
}
