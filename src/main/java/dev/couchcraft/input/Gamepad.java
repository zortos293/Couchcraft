package dev.couchcraft.input;

import dev.couchcraft.context.PlayerContext;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLGamepad;

/**
 * One physical controller, read through SDL3's gamepad API (standard Xbox-style layout for every brand).
 * Triggers are exposed as two extra digital buttons so bindings treat them like any other button.
 */
public final class Gamepad {
	public static final int A = SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH;
	public static final int B = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;
	public static final int X = SDLGamepad.SDL_GAMEPAD_BUTTON_WEST;
	public static final int Y = SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH;
	public static final int BACK = SDLGamepad.SDL_GAMEPAD_BUTTON_BACK;
	public static final int START = SDLGamepad.SDL_GAMEPAD_BUTTON_START;
	public static final int L3 = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK;
	public static final int R3 = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK;
	public static final int LB = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER;
	public static final int RB = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER;
	public static final int DPAD_UP = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP;
	public static final int DPAD_DOWN = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN;
	public static final int DPAD_LEFT = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT;
	public static final int DPAD_RIGHT = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT;
	private static final int SDL_BUTTONS = 15;
	public static final int LT = SDL_BUTTONS;
	public static final int RT = SDL_BUTTONS + 1;
	private static final int BUTTON_COUNT = SDL_BUTTONS + 2;

	private final int instanceId;
	private final long handle;
	private final String name;
	private final boolean[] down = new boolean[BUTTON_COUNT];
	private final boolean[] previous = new boolean[BUTTON_COUNT];
	/** Buttons whose current press was used for something else (joining, claiming) and must be released first. */
	private final boolean[] consumed = new boolean[BUTTON_COUNT];
	private float leftX;
	private float leftY;
	private float rightX;
	private float rightY;
	private float leftTrigger;
	private float rightTrigger;
	private boolean connected = true;
	private @Nullable PlayerContext owner;
	private long identifyUntil;
	private boolean ledLit;

	Gamepad(final int instanceId, final long handle, final String name) {
		this.instanceId = instanceId;
		this.handle = handle;
		this.name = name;
	}

	void update() {
		System.arraycopy(this.down, 0, this.previous, 0, BUTTON_COUNT);
		for (int button = 0; button < SDL_BUTTONS; button++) {
			this.down[button] = SDLGamepad.SDL_GetGamepadButton(this.handle, button);
		}

		this.leftX = axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX);
		this.leftY = axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY);
		this.rightX = axis(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX);
		this.rightY = axis(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY);
		this.leftTrigger = Math.max(0.0F, axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER));
		this.rightTrigger = Math.max(0.0F, axis(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER));
		// Hysteresis so a trigger resting near the threshold doesn't chatter.
		this.down[LT] = this.leftTrigger > (this.previous[LT] ? 0.35F : 0.5F);
		this.down[RT] = this.rightTrigger > (this.previous[RT] ? 0.35F : 0.5F);

		for (int button = 0; button < BUTTON_COUNT; button++) {
			if (this.consumed[button] && !this.down[button]) {
				this.consumed[button] = false;
			}
		}

		this.updateIdentifyBlink();
	}

	/**
	 * "Which one is this?": a strong buzz and a blinking light bar / player LED for about a second, then the light
	 * returns to its owner's colour.
	 */
	public void identify() {
		if (!this.connected) {
			return;
		}

		this.identifyUntil = System.currentTimeMillis() + 1000L;
		this.rumble(1.0F, 1.0F, 700);
	}

	public boolean isIdentifying() {
		return System.currentTimeMillis() < this.identifyUntil;
	}

	private void updateIdentifyBlink() {
		if (this.identifyUntil == 0L) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now >= this.identifyUntil) {
			this.identifyUntil = 0L;
			this.ledLit = false;
			this.applyOwnerLights();
			return;
		}

		boolean lit = now / 125L % 2L == 0L;
		if (lit != this.ledLit) {
			this.ledLit = lit;
			byte value = (byte)(lit ? 0xFF : 0x00);
			SDLGamepad.SDL_SetGamepadLED(this.handle, value, value, value);
			SDLGamepad.SDL_SetGamepadPlayerIndex(this.handle, lit ? (this.owner == null ? 0 : this.owner.slot) : -1);
		}
	}

	private void applyOwnerLights() {
		if (!this.connected) {
			return;
		}

		if (this.owner == null) {
			SDLGamepad.SDL_SetGamepadPlayerIndex(this.handle, -1);
			SDLGamepad.SDL_SetGamepadLED(this.handle, (byte)0, (byte)0, (byte)0);
		} else {
			int color = this.owner.color();
			SDLGamepad.SDL_SetGamepadPlayerIndex(this.handle, this.owner.slot);
			SDLGamepad.SDL_SetGamepadLED(this.handle, (byte)(color >> 16 & 0xFF), (byte)(color >> 8 & 0xFF), (byte)(color & 0xFF));
		}
	}

	private float axis(final int axis) {
		return Math.max(-1.0F, SDLGamepad.SDL_GetGamepadAxis(this.handle, axis) / 32767.0F);
	}

	public boolean pressed(final int button) {
		return this.down[button] && !this.previous[button] && !this.consumed[button];
	}

	public boolean released(final int button) {
		return !this.down[button] && this.previous[button];
	}

	public boolean held(final int button) {
		return this.down[button] && !this.consumed[button];
	}

	/** Any button held or any stick/trigger off-centre: someone is playing on this pad. */
	public boolean isActive() {
		for (boolean button : this.down) {
			if (button) {
				return true;
			}
		}

		return Math.abs(this.leftX) > 0.25F
			|| Math.abs(this.leftY) > 0.25F
			|| Math.abs(this.rightX) > 0.25F
			|| Math.abs(this.rightY) > 0.25F
			|| this.leftTrigger > 0.25F
			|| this.rightTrigger > 0.25F;
	}

	public boolean anyPressed() {
		for (int button = 0; button < BUTTON_COUNT; button++) {
			if (this.pressed(button)) {
				return true;
			}
		}

		return false;
	}

	/** Ignores every currently held button until it is released, so the press that claimed a pad does nothing else. */
	public void consumeAll() {
		for (int button = 0; button < BUTTON_COUNT; button++) {
			this.consumed[button] = this.down[button];
		}
	}

	public float leftX() {
		return this.leftX;
	}

	public float leftY() {
		return this.leftY;
	}

	public float rightX() {
		return this.rightX;
	}

	public float rightY() {
		return this.rightY;
	}

	public float leftTrigger() {
		return this.leftTrigger;
	}

	public float rightTrigger() {
		return this.rightTrigger;
	}

	public String name() {
		return this.name;
	}

	int instanceId() {
		return this.instanceId;
	}

	long handle() {
		return this.handle;
	}

	public boolean isConnected() {
		return this.connected;
	}

	void markDisconnected() {
		this.connected = false;
		java.util.Arrays.fill(this.down, false);
	}

	public @Nullable PlayerContext owner() {
		return this.owner;
	}

	/** Gives the pad to a player and lights its player LED / light bar in their color. */
	public void claim(final PlayerContext context) {
		this.owner = context;
		if (this.connected) {
			this.applyOwnerLights();
			this.rumble(0.35F, 0.35F, 160);
		}
	}

	public void release() {
		this.owner = null;
		this.applyOwnerLights();
	}

	public void rumble(final float low, final float high, final int millis) {
		if (this.connected) {
			SDLGamepad.SDL_RumbleGamepad(this.handle, toShort(low), toShort(high), millis);
		}
	}

	private static short toShort(final float strength) {
		return (short)Math.round(Math.clamp(strength, 0.0F, 1.0F) * 0xFFFF);
	}
}
