package dev.couchcraft.dev;

import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLJoystick;
import org.lwjgl.sdl.SDL_VirtualJoystickDesc;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * A virtual standard-layout gamepad created through SDL3, so the dev harness can drive the real controller code path
 * (discovery, join, bindings, cursor) without hardware.
 */
final class DevPad {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final String label;
	private long joystick;
	private ByteBuffer name;

	DevPad(final String label) {
		this.label = label;
	}

	boolean attach() {
		this.name = MemoryUtil.memUTF8(this.label);
		try (SDL_VirtualJoystickDesc desc = SDL_VirtualJoystickDesc.calloc()) {
			desc.version(SDL_VirtualJoystickDesc.SIZEOF);
			desc.type((short)SDLJoystick.SDL_JOYSTICK_TYPE_GAMEPAD);
			desc.naxes((short)SDLGamepad.SDL_GAMEPAD_AXIS_COUNT);
			desc.nbuttons((short)SDLGamepad.SDL_GAMEPAD_BUTTON_COUNT);
			desc.name(this.name);
			int id = SDLJoystick.SDL_AttachVirtualJoystick(desc);
			if (id == 0) {
				LOGGER.warn("[Couchcraft/dev] Could not attach virtual pad: {}", SDLError.SDL_GetError());
				return false;
			}

			this.joystick = SDLJoystick.SDL_OpenJoystick(id);
			LOGGER.info("[Couchcraft/dev] Virtual pad attached (id {})", id);
			return this.joystick != 0L;
		}
	}

	void button(final int button, final boolean down) {
		SDLJoystick.SDL_SetJoystickVirtualButton(this.joystick, button, down);
	}

	/** @param value -1..1 (triggers 0..1) */
	void axis(final int axis, final float value) {
		SDLJoystick.SDL_SetJoystickVirtualAxis(this.joystick, axis, (short)Math.round(Math.clamp(value, -1.0F, 1.0F) * 32767.0F));
	}
}
