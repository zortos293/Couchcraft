package dev.couchcraft.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import dev.couchcraft.net.GuestDisconnects;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
	@Shadow
	@Final
	protected Connection connection;

	@Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guestOnly(final DisconnectionDetails details, final CallbackInfo ci) {
		if (GuestDisconnects.handle(this.connection, details)) {
			ci.cancel();
		}
	}
}
