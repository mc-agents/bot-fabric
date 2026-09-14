package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.LocalChatSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
    @Accessor("serverEnforcesSecureChat")
    boolean mcagents$serverEnforcesSecureChat();

    @Accessor("chatSession")
    LocalChatSession mcagents$chatSession();
}
