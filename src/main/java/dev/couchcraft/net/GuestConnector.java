package dev.couchcraft.net;

import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import java.net.SocketAddress;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

/**
 * Logs a guest into the integrated server over an in-process memory channel, exactly the way vanilla
 * logs in the host. Memory connections skip authentication, so the server creates an offline profile from the name.
 */
public final class GuestConnector {
	private static final Map<IntegratedServer, SocketAddress> MEMORY_ADDRESSES = new WeakHashMap<>();

	private GuestConnector() {
	}

	public static Connection connect(final Minecraft minecraft, final PlayerContext guest) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null) {
			throw new IllegalStateException("Guests can only join a singleplayer world");
		}

		SocketAddress address = MEMORY_ADDRESSES.computeIfAbsent(server, s -> s.getConnection().startMemoryChannel());
		Connection connection = Connection.connectToLocalServer(address);
		try (Couch.Scope ignored = Couch.enter(guest)) {
			ClientHandshakePacketListenerImpl listener = new ClientHandshakePacketListenerImpl(
				connection, minecraft, null, null, false, null, status -> {}, new LevelLoadTracker(), null
			);
			connection.initiateServerboundPlayConnection(address.toString(), 0, listener);
			connection.send(new ServerboundHelloPacket(guest.name, guest.profileId));
			// Vanilla ticks a connection that is still logging in through Minecraft.pendingConnection.
			((dev.couchcraft.mixin.access.MinecraftAccessor)minecraft).couchcraft$setPendingConnection(connection);
		}

		return connection;
	}
}
