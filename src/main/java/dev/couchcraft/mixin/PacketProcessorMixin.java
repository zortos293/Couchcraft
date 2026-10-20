package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.mixin.access.ClientCommonPacketListenerImplAccessor;
import dev.couchcraft.mixin.access.ClientHandshakePacketListenerImplAccessor;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Every packet handled on the game thread passes through here. A packet for a guest's connection is handled
 * with that guest live on the singleton, so vanilla handlers that say "minecraft.player" mean that guest.
 */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorMixin {
	@Shadow
	@Final
	private PacketListener listener;

	@WrapMethod(method = "handle")
	private void couchcraft$routeToPlayer(final Operation<Void> original) {
		Connection connection = switch (this.listener) {
			case ClientCommonPacketListenerImpl common -> ((ClientCommonPacketListenerImplAccessor)common).couchcraft$connection();
			case ClientHandshakePacketListenerImpl handshake -> ((ClientHandshakePacketListenerImplAccessor)handshake).couchcraft$connection();
			default -> null;
		};

		if (Couch.isGuestConnection(connection)) {
			PlayerContext guest = Couch.byConnection(connection);
			if (guest == null || guest.isRemoved()) {
				// Late packet for a player who already left: their state no longer exists.
				return;
			}

			try (Couch.Scope ignored = Couch.enter(guest)) {
				original.call();
			}
		} else if (connection != null) {
			Couch.asPrimary(original::call);
		} else {
			original.call();
		}
	}
}
