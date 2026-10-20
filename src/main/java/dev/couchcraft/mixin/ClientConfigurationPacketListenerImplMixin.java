package dev.couchcraft.mixin;

import dev.couchcraft.mixin.access.ClientCommonPacketListenerImplAccessor;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import dev.couchcraft.net.GuestDisconnects;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerImplMixin {
	@Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guestOnly(final DisconnectionDetails details, final CallbackInfo ci) {
		if (GuestDisconnects.handle(((ClientCommonPacketListenerImplAccessor)this).couchcraft$connection(), details)) {
			ci.cancel();
		}
	}
}
