package shizo.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shizo.module.impl.misc.SeeThrough;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void onRenderBlockSuffocation(CallbackInfo ci) {
        if (SeeThrough.INSTANCE.getEnabled() && SeeThrough.INSTANCE.getSeeThroughBlocks()) {
            ci.cancel();
        }
    }
}