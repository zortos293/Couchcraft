package dev.couchcraft.sound;

import com.mojang.blaze3d.audio.ListenerTransform;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * One pair of speakers, up to four players.
 * <p>
 * Every guest has its own copy of the world, so the server sends each world the same sounds. Duplicates are dropped
 * here. Positional sounds are then expressed relative to the <em>nearest</em> player's head, with the OpenAL listener
 * parked at the origin, so every player hears what is close to them with correct distance and direction.
 */
public final class SoundRouter {
	public static final ListenerTransform ORIGIN_LISTENER = new ListenerTransform(Vec3.ZERO, new Vec3(0.0, 0.0, -1.0), new Vec3(0.0, 1.0, 0.0));
	private static final long DUPLICATE_WINDOW_MILLIS = 120L;
	private static final Long2LongOpenHashMap RECENT = new Long2LongOpenHashMap();
	private static final List<Ear> EARS = new ArrayList<>();
	private static long lastCleanup;
	private static long played;
	private static long dropped;

	private SoundRouter() {
	}

	private record Ear(Vec3 position, Vec3 forward, Vec3 up, Vec3 right) {
	}

	public static boolean active() {
		return Couch.ready() && Couch.isSplit();
	}

	/** Snapshot every player's head, once per frame, on the render thread. */
	public static void updateEars() {
		EARS.clear();
		for (PlayerContext context : Couch.contexts()) {
			Camera camera = context.gameRenderer.mainCamera();
			if (camera.isInitialized()) {
				Vec3 forward = new Vec3(camera.forwardVector());
				Vec3 up = new Vec3(camera.upVector());
				EARS.add(new Ear(camera.position(), forward, up, forward.cross(up)));
			}
		}
	}

	/** False if an identical sound already started at the same place a moment ago. */
	public static boolean shouldPlay(final SoundInstance instance) {
		if (!active()) {
			return true;
		}

		long now = Util.getMillis();
		if (now - lastCleanup > 2000L) {
			lastCleanup = now;
			RECENT.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > DUPLICATE_WINDOW_MILLIS);
		}

		long key = instance.getIdentifier().hashCode();
		key = key * 31 + instance.getSource().ordinal();
		key = key * 31 + Math.round(instance.getX() * 2.0);
		key = key * 31 + Math.round(instance.getY() * 2.0);
		key = key * 31 + Math.round(instance.getZ() * 2.0);
		key = key * 31 + (instance.isRelative() ? 1 : 0);
		long last = RECENT.getOrDefault(key, Long.MIN_VALUE);
		if (last != Long.MIN_VALUE && now - last < DUPLICATE_WINDOW_MILLIS) {
			dropped++;
			return false;
		}

		RECENT.put(key, now);
		played++;
		return true;
	}

	/** "played/dropped" since launch, for diagnostics. */
	public static String stats() {
		return played + " played, " + dropped + " duplicates dropped";
	}

	/** World position to listener space of the nearest player. Relative sounds are already in listener space. */
	public static Vec3 toListenerSpace(final Vec3 world, final boolean relative) {
		if (!active() || relative || EARS.isEmpty()) {
			return world;
		}

		Ear nearest = EARS.getFirst();
		double best = Double.MAX_VALUE;
		for (Ear ear : EARS) {
			double distance = ear.position.distanceToSqr(world);
			if (distance < best) {
				best = distance;
				nearest = ear;
			}
		}

		Vec3 delta = world.subtract(nearest.position);
		return new Vec3(delta.dot(nearest.right), delta.dot(nearest.up), -delta.dot(nearest.forward));
	}
}
