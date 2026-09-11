package kr.junhyung.mcagents.botfabric.mixin;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("user")
    void botfabric$setUser(User user);

    @Mutable
    @Accessor("profileFuture")
    void botfabric$setProfileFuture(CompletableFuture<ProfileResult> future);
}
