package shizo.mixin;

import shizo.interfaces.IChatComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent implements IChatComponent {

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    protected abstract double screenToChatX(double d);

    @Shadow
    protected abstract double screenToChatY(double d);

    @Shadow
    protected abstract int getMessageLineIndexAt(double d, double e);

    @Override
    public double getMouseXtoChatX() {
        Minecraft mc = Minecraft.getInstance();
        return screenToChatX(mc.mouseHandler.getScaledXPos(mc.getWindow()));
    }

    @Override
    public double getMouseYtoChatY() {
        Minecraft mc = Minecraft.getInstance();
        return screenToChatY(mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }

    @Override
    public double getLineIndex(double x, double y) {
        return getMessageLineIndexAt(x, y);
    }

    @Override
    public List<GuiMessage.Line> getVisibleMessages() {
        return this.trimmedMessages;
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void clearMessages(CallbackInfo ci) {
        ci.cancel();
    }
}