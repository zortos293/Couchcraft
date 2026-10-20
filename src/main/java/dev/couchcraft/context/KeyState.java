package dev.couchcraft.context;

import dev.couchcraft.mixin.access.KeyMappingAccessor;
import dev.couchcraft.mixin.access.ToggleKeyMappingAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;

/**
 * One player's copy of every key binding's live state. {@code KeyMapping} keeps that state in static-reachable
 * instances, so it is saved and restored whenever the active player changes.
 */
public final class KeyState {
	private static final int DOWN = 1;
	private static final int RELEASED_BY_SCREEN = 2;

	private final Map<KeyMapping, int[]> states = new IdentityHashMap<>();

	public void saveFromLive() {
		for (KeyMapping mapping : KeyMappingAccessor.couchcraft$all().values()) {
			KeyMappingAccessor access = (KeyMappingAccessor)mapping;
			int flags = access.couchcraft$isDownRaw() ? DOWN : 0;
			if (mapping instanceof ToggleKeyMapping && ((ToggleKeyMappingAccessor)mapping).couchcraft$releasedByScreen()) {
				flags |= RELEASED_BY_SCREEN;
			}

			int[] state = this.states.computeIfAbsent(mapping, k -> new int[2]);
			state[0] = flags;
			state[1] = access.couchcraft$clickCount();
		}
	}

	public void loadIntoLive() {
		for (KeyMapping mapping : KeyMappingAccessor.couchcraft$all().values()) {
			KeyMappingAccessor access = (KeyMappingAccessor)mapping;
			int[] state = this.states.get(mapping);
			int flags = state == null ? 0 : state[0];
			access.couchcraft$setDownRaw((flags & DOWN) != 0);
			access.couchcraft$setClickCount(state == null ? 0 : state[1]);
			if (mapping instanceof ToggleKeyMapping) {
				((ToggleKeyMappingAccessor)mapping).couchcraft$setReleasedByScreen((flags & RELEASED_BY_SCREEN) != 0);
			}
		}
	}
}
