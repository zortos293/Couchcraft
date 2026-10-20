package dev.couchcraft.net;

import com.mojang.logging.LogUtils;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Vanilla reacts to a lost connection by quitting to the title screen. For a guest that would end everyone's game,
 * so a guest's disconnect only removes that guest.
 */
public final class GuestDisconnects {
	private static final Logger LOGGER = LogUtils.getLogger();

	private GuestDisconnects() {
	}

	/** @return true if the disconnect belonged to a guest and has been handled */
	public static boolean handle(final @Nullable Connection connection, final DisconnectionDetails details) {
		if (!Couch.isGuestConnection(connection)) {
			return false;
		}

		PlayerContext guest = Couch.byConnection(connection);
		if (guest != null) {
			LOGGER.info("[Couchcraft] {} disconnected: {}", guest.name, details.reason().getString());
			Couch.requestRemove(guest);
		}

		return true;
	}
}
