package shizo.mixin;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shizo.Shizo;

/* NEVER DELETE THIS OR WE GET BANNED*/

@Pseudo
@Mixin(targets = "moe.nea.firmament.features.misc.ModAnnouncer", remap = false)
public class FuckFirmament {
    @Dynamic
    @Inject(method = "onServerJoin", at = @At("HEAD"), cancellable = true)
    private void stopFeddingEveryone(@Coerce Object event, CallbackInfo ci) {
        ci.cancel();
        Shizo.INSTANCE.getLogger().info("FUCK FIRMAMENT");
    }
}