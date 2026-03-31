package shizo.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shizo.events.GuiEvent;

@Mixin(AbstractContainerScreen.class)
public class WardrobeContainerMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onWardrobeKeyPress(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {

        if (new GuiEvent.ContainerKeyPress((Screen) (Object) this, input).postAndCatch()) {

            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}