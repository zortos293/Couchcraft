package dev.couchcraft.input;

import com.mojang.logging.LogUtils;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLStdinc;
import org.slf4j.Logger;

/**
 * Discovers and polls controllers. Vanilla only initialises SDL's video subsystem; the gamepad subsystem is added
 * lazily on the render thread the first time a frame runs.
 */
public final class Gamepads {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int REFRESH_INTERVAL_FRAMES = 30;
	private static final Map<Integer, Gamepad> PADS = new LinkedHashMap<>();
	private static boolean initAttempted;
	private static boolean available;
	private static int refreshCountdown;

	private Gamepads() {
	}

	public static void poll() {
		if (!initAttempted) {
			initAttempted = true;
			// Couch players are often looking at a TV while the window has focus elsewhere; keep reading pads.
			SDLHints.SDL_SetHint("SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS", "1");
			available = SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD);
			if (available) {
				LOGGER.info("[Couchcraft] Controller support ready");
			} else {
				LOGGER.warn("[Couchcraft] Could not start SDL gamepad support: {}", SDLError.SDL_GetError());
			}
		}

		if (!available) {
			return;
		}

		SDLGamepad.SDL_UpdateGamepads();
		if (--refreshCountdown <= 0) {
			refreshCountdown = REFRESH_INTERVAL_FRAMES;
			refresh();
		}

		for (Gamepad pad : PADS.values()) {
			pad.update();
		}
	}

	private static void refresh() {
		Set<Integer> present = new HashSet<>();
		IntBuffer ids = SDLGamepad.SDL_GetGamepads();
		if (ids != null) {
			try {
				for (int i = ids.position(); i < ids.limit(); i++) {
					present.add(ids.get(i));
				}
			} finally {
				SDLStdinc.SDL_free(ids);
			}
		}

		Iterator<Gamepad> iterator = PADS.values().iterator();
		while (iterator.hasNext()) {
			Gamepad pad = iterator.next();
			if (!present.contains(pad.instanceId()) || !SDLGamepad.SDL_GamepadConnected(pad.handle())) {
				LOGGER.info("[Couchcraft] Controller removed: {}", pad.name());
				pad.markDisconnected();
				SDLGamepad.SDL_CloseGamepad(pad.handle());
				iterator.remove();
			}
		}

		for (int id : present) {
			if (!PADS.containsKey(id)) {
				long handle = SDLGamepad.SDL_OpenGamepad(id);
				if (handle != 0L) {
					String name = SDLGamepad.SDL_GetGamepadName(handle);
					Gamepad pad = new Gamepad(id, handle, name == null ? "Controller" : name);
					SDLGamepad.SDL_SetGamepadPlayerIndex(handle, -1);
					PADS.put(id, pad);
					LOGGER.info("[Couchcraft] Controller connected: {}", pad.name());
				}
			}
		}
	}

	public static List<Gamepad> connected() {
		return new ArrayList<>(PADS.values());
	}

	/** 1-based position in connection order, so two identical controllers can be told apart ("2 · DualSense"). */
	public static int number(final Gamepad pad) {
		int number = 1;
		for (Gamepad other : PADS.values()) {
			if (other == pad) {
				return number;
			}

			number++;
		}

		return 0;
	}

	public static boolean anyUnclaimed() {
		for (Gamepad pad : PADS.values()) {
			if (pad.owner() == null) {
				return true;
			}
		}

		return false;
	}
}
