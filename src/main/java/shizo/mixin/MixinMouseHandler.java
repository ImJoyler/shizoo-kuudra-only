package shizo.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shizo.Shizo;
import shizo.events.ClickEvent;
import shizo.module.impl.render.NoCursorReset;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long l, MouseButtonInfo mouseButtonInfo, int i, CallbackInfo ci) {
        if (l != minecraft.getWindow().handle()) return;
        if (new ClickEvent(mouseButtonInfo.button(), i, mouseButtonInfo.modifiers()).postAndCatch()) {
            ci.cancel();
        }
    }
    @Shadow
    private double xpos;
    @Shadow
    private double ypos;

    @Unique
    private double beforeX;
    @Unique
    private double beforeY;

    @Inject(method = "grabMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;xpos:D", ordinal = 0, opcode = Opcodes.PUTFIELD))
    private void odin$lockXPos(CallbackInfo ci) {
        this.beforeX = this.xpos;
        this.beforeY = this.ypos;
    }

    @Inject(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;"))
    private void odin$correctCursorPosition(CallbackInfo ci) {
        if (Shizo.getMc().screen instanceof ContainerScreen && NoCursorReset.shouldHookMouse()) {
            InputConstants.grabOrReleaseMouse(Shizo.getMc().getWindow(), InputConstants.CURSOR_NORMAL, this.beforeX, this.beforeY);
            this.xpos = this.beforeX;
            this.ypos = this.beforeY;
        }
    }
    // tod make it not work inside gui // scratch that we fixed it
}