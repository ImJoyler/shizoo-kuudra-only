package shizo.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shizo.events.PacketEvent;
import shizo.events.TickEvent;


@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Shadow
    private volatile @Nullable PacketListener packetListener;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {

        if (Minecraft.getInstance().isSingleplayer() && Minecraft.getInstance().player != null) {
            ServerGamePacketListenerImpl gamePacketListener = (ServerGamePacketListenerImpl) this.packetListener;
        }

        if (packet instanceof ClientboundPingPacket pingPacket && pingPacket.getId() != 0) {
            new TickEvent.Server().postAndCatch();
        }
        if (new PacketEvent.Receive(packet).postAndCatch()) ci.cancel();
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void sendImmediately(Packet<?> packet, ChannelFutureListener channelFutureListener, boolean flush, CallbackInfo ci) {
        if (new PacketEvent.Send(packet).postAndCatch()) ci.cancel();
    }
}