package shizo.mixin;


import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shizo.events.MainThreadPacketPost;
import shizo.events.MainThreadPacketPre;
import shizo.events.PacketEvent;
import shizo.events.core.EventBus;
// to use for things we don't need to process instatnly for performance and for ms
//WIP

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public class PacketListenerMixin {
    @Shadow
    @Final
    private Packet<?> packet;

    @Inject(
            method = "handle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),
            cancellable = true
    )
    private void onPreHandle(CallbackInfo ci) {
        if (new MainThreadPacketPre(this.packet).postAndCatch()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V", shift = At.Shift.AFTER)
    )
    private void onPostHandle(CallbackInfo ci) {
        // Calling the new standalone class
        new MainThreadPacketPost(this.packet).postAndCatch();
    }
}