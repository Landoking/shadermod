package com.linearcy.shadercontrol.mixin;

import com.linearcy.shadercontrol.ShaderControlClient;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void shaderControl$override(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (!ShaderControlClient.overrideEnabled) return;
        byte[] bytes = ShaderControlClient.OVERRIDES.get(id);
        Optional<Resource> original = cir.getReturnValue();
        if (bytes == null || original.isEmpty()) return;
        Resource base = original.get();
        try {
            Resource replacement = new Resource(base.getPack(), () -> new ByteArrayInputStream(bytes), base::metadata);
            cir.setReturnValue(Optional.of(replacement));
        } catch (Throwable ignored) {}
    }
}
