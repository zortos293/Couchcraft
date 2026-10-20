package dev.couchcraft.mixin;

import dev.couchcraft.mixin.access.ClientHandshakePacketListenerImplAccessor;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import dev.couchcraft.net.GuestDisconnects;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {
	@Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guestOnly(final DisconnectionDetails details, final CallbackInfo ci) {
		if (GuestDisconnects.handle(((ClientHandshakePacketListenerImplAccessor)this).couchcraft$connection(), details)) {
			ci.cancel();
		}
	}
}
