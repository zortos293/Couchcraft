package dev.couchcraft.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.couchcraft.CouchConfig;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.context.VirtualWindow;
import dev.couchcraft.mixin.access.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLMouse;

/**
 * Turns one player's controller into vanilla input, every frame, while that player's context is live.
 * In the world it drives key bindings, analog look and the hotbar; in menus it drives a virtual cursor.
 */
public final class PadController {
	private static final int MOUSE_LEFT = 1;
	private static final int MOUSE_RIGHT = 3;
	private static final int SHIFT_MODIFIER = 1;
	private static final int SDLK_ESCAPE = 27;
	private static final long LEAVE_HOLD_NANOS = 1_200_000_000L;

	private final PlayerContext context;
	/** Which world bindings this controller is currently holding down, so releases always match presses. */
	private final boolean[] holding = new boolean[Binding.values().length];
	private long lastFrameNanos = Util.getNanos();
	private long leaveComboSince = -1L;
	private @Nullable Screen cursorScreen;
	private double cursorX;
	private double cursorY;
	private double scrollAccumulator;
	private int heldMenuButton = -1;
	private int lastHurtTime;

	public PadController(final PlayerContext context) {
		this.context = context;
	}

	/** Gameplay key bindings driven by face buttons, sticks and triggers. */
	private enum Binding {
		JUMP(Gamepad.A),
		DROP(Gamepad.B),
		PICK(Gamepad.X),
		INVENTORY(Gamepad.Y),
		ATTACK(Gamepad.RT),
		USE(Gamepad.LT),
		SPRINT(Gamepad.L3),
		PLAYER_LIST(Gamepad.BACK),
		PERSPECTIVE(Gamepad.DPAD_UP),
		SWAP_HANDS(Gamepad.DPAD_DOWN);

		final int button;

		Binding(final int button) {
			this.button = button;
		}

		KeyMapping mapping(final Options options) {
			return switch (this) {
				case JUMP -> options.keyJump;
				case DROP -> options.keyDrop;
				case PICK -> options.keyPickItem;
				case INVENTORY -> options.keyInventory;
				case ATTACK -> options.keyAttack;
				case USE -> options.keyUse;
				case SPRINT -> options.keySprint;
				case PLAYER_LIST -> options.keyPlayerList;
				case PERSPECTIVE -> options.keyTogglePerspective;
				case SWAP_HANDS -> options.keySwapOffhand;
			};
		}
	}

	public void frame(final Minecraft minecraft) {
		long now = Util.getNanos();
		float dt = Math.clamp((now - this.lastFrameNanos) / 1.0E9F, 0.0F, 0.1F);
		this.lastFrameNanos = now;
		Gamepad pad = this.context.pad;
		if (pad == null) {
			this.releaseWorldBindings(minecraft.options);
			return;
		}

		if (!this.context.primary && this.checkLeaveCombo(pad, now)) {
			return;
		}

		Screen screen = minecraft.gui.screen();
		if (screen != null || minecraft.player == null) {
			this.releaseWorldBindings(minecraft.options);
			if (screen != null) {
				this.menuFrame(minecraft, pad, screen, dt);
			}
		} else {
			this.cursorScreen = null;
			this.worldFrame(minecraft, pad, minecraft.player, dt);
		}

		this.rumbleOnDamage(minecraft.player, pad);
	}

	private boolean checkLeaveCombo(final Gamepad pad, final long now) {
		if (pad.held(Gamepad.BACK) && pad.held(Gamepad.START)) {
			if (this.leaveComboSince < 0L) {
				this.leaveComboSince = now;
			} else if (now - this.leaveComboSince > LEAVE_HOLD_NANOS) {
				this.leaveComboSince = -1L;
				pad.consumeAll();
				pad.rumble(0.6F, 0.2F, 250);
				Couch.requestRemove(this.context);
				return true;
			}
		} else {
			this.leaveComboSince = -1L;
		}

		return false;
	}

	// ---------------------------------------------------------------- in the world

	private void worldFrame(final Minecraft minecraft, final Gamepad pad, final LocalPlayer player, final float dt) {
		Options options = minecraft.options;
		for (Binding binding : Binding.values()) {
			KeyMapping mapping = binding.mapping(options);
			if (pad.pressed(binding.button)) {
				mapping.setDown(true);
				KeyMappingAccessor access = (KeyMappingAccessor)mapping;
				access.couchcraft$setClickCount(access.couchcraft$clickCount() + 1);
				this.holding[binding.ordinal()] = true;
			} else if (this.holding[binding.ordinal()] && !pad.held(binding.button)) {
				mapping.setDown(false);
				this.holding[binding.ordinal()] = false;
			}
		}

		if (pad.pressed(Gamepad.R3)) {
			// Sneak is a toggle on a pad regardless of the keyboard option: holding a stick down is miserable.
			KeyMappingAccessor sneak = (KeyMappingAccessor)options.keyShift;
			sneak.couchcraft$setDownRaw(!sneak.couchcraft$isDownRaw());
		}

		if (pad.pressed(Gamepad.LB) || pad.pressed(Gamepad.RB)) {
			Inventory inventory = player.getInventory();
			int step = pad.pressed(Gamepad.RB) ? 1 : -1;
			if (player.isSpectator()) {
				minecraft.gui.hud.getSpectatorGui().onMouseScrolled(-step);
			} else {
				inventory.setSelectedSlot(Math.floorMod(inventory.getSelectedSlot() + step, Inventory.getSelectionSize()));
			}
		}

		if (pad.pressed(Gamepad.START)) {
			minecraft.pauseGame(false);
			return;
		}

		this.look(player, pad, dt);
	}

	private void look(final LocalPlayer player, final Gamepad pad, final float dt) {
		CouchConfig config = CouchConfig.get();
		float x = PadMath.curve(pad.rightX(), config.stickDeadzone);
		float y = PadMath.curve(pad.rightY(), config.stickDeadzone);
		if (x == 0.0F && y == 0.0F) {
			return;
		}

		float scale = player.isScoping() ? 0.2F : 1.0F;
		double yawDegrees = x * config.lookSpeedYaw * dt * scale;
		double pitchDegrees = y * config.lookSpeedPitch * dt * scale * (config.invertLookY ? -1.0F : 1.0F);
		// Entity.turn works in mouse units of 0.15 degrees.
		player.turn(yawDegrees / 0.15, pitchDegrees / 0.15);
	}

	private void releaseWorldBindings(final Options options) {
		for (Binding binding : Binding.values()) {
			if (this.holding[binding.ordinal()]) {
				binding.mapping(options).setDown(false);
				this.holding[binding.ordinal()] = false;
			}
		}
	}

	// ---------------------------------------------------------------- in menus

	private void menuFrame(final Minecraft minecraft, final Gamepad pad, final Screen screen, final float dt) {
		Window window = minecraft.getWindow();
		VirtualWindow.Frame frame = VirtualWindow.active();
		double originX = frame == null ? 0.0 : frame.screenX();
		double originY = frame == null ? 0.0 : frame.screenY();
		int screenWidth = window.getScreenWidth();
		int screenHeight = window.getScreenHeight();
		double screenPerGui = (double)screenWidth / Math.max(1, window.getGuiScaledWidth());

		if (this.cursorScreen != screen) {
			this.cursorScreen = screen;
			this.cursorX = screenWidth / 2.0;
			this.cursorY = screenHeight / 2.0;
			this.moveCursor(minecraft, originX, originY, 0.0, 0.0);
		} else if (this.context.primary) {
			// The real mouse may have moved since last frame; follow it.
			this.cursorX = minecraft.mouseHandler.xpos() - originX;
			this.cursorY = minecraft.mouseHandler.ypos() - originY;
		}

		CouchConfig config = CouchConfig.get();
		float sx = PadMath.curve(pad.leftX(), config.stickDeadzone);
		float sy = PadMath.curve(pad.leftY(), config.stickDeadzone);
		double dx = sx * config.cursorSpeed * screenPerGui * dt;
		double dy = sy * config.cursorSpeed * screenPerGui * dt;
		double slot = 18.0 * screenPerGui;
		if (pad.pressed(Gamepad.DPAD_LEFT)) {
			dx -= slot;
		}

		if (pad.pressed(Gamepad.DPAD_RIGHT)) {
			dx += slot;
		}

		if (pad.pressed(Gamepad.DPAD_UP)) {
			dy -= slot;
		}

		if (pad.pressed(Gamepad.DPAD_DOWN)) {
			dy += slot;
		}

		if (dx != 0.0 || dy != 0.0) {
			double oldX = this.cursorX;
			double oldY = this.cursorY;
			this.cursorX = Math.clamp(this.cursorX + dx, 0.0, screenWidth - 1);
			this.cursorY = Math.clamp(this.cursorY + dy, 0.0, screenHeight - 1);
			this.moveCursor(minecraft, originX, originY, this.cursorX - oldX, this.cursorY - oldY);
		}

		long handle = window.handle();
		this.mirrorButton(minecraft, pad, handle, Gamepad.A, MOUSE_LEFT, 0);
		this.mirrorButton(minecraft, pad, handle, Gamepad.X, MOUSE_LEFT, SHIFT_MODIFIER);
		this.mirrorButton(minecraft, pad, handle, Gamepad.Y, MOUSE_RIGHT, 0);

		float scroll = PadMath.curve(pad.rightY(), config.stickDeadzone);
		this.scrollAccumulator += -scroll * dt * 14.0;
		while (Math.abs(this.scrollAccumulator) >= 1.0) {
			double step = Math.signum(this.scrollAccumulator);
			this.scrollAccumulator -= step;
			minecraft.mouseHandler.onScroll(handle, 0.0, step);
		}

		if (pad.pressed(Gamepad.B) || pad.pressed(Gamepad.START) && screen instanceof PauseScreen) {
			screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, SDLK_ESCAPE, 0));
		}
	}

	private void moveCursor(final Minecraft minecraft, final double originX, final double originY, final double dx, final double dy) {
		Window window = minecraft.getWindow();
		if (this.context.primary) {
			// The primary's cursor is the real OS cursor: warp it and let SDL report the motion.
			SDLMouse.SDL_WarpMouseInWindow(window.handle(), (float)(originX + this.cursorX), (float)(originY + this.cursorY));
		} else {
			minecraft.mouseHandler.onMove(window.handle(), this.cursorX, this.cursorY, dx, dy);
		}
	}

	/** Mirrors a pad button onto a mouse button, so dragging items and holding buttons behave like a real mouse. */
	private void mirrorButton(final Minecraft minecraft, final Gamepad pad, final long handle, final int padButton, final int mouseButton, final int modifiers) {
		if (pad.pressed(padButton) && this.heldMenuButton < 0) {
			this.heldMenuButton = padButton;
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(mouseButton, modifiers), 1);
		} else if (this.heldMenuButton == padButton && !pad.held(padButton)) {
			this.heldMenuButton = -1;
			minecraft.mouseHandler.onButton(handle, new MouseButtonInfo(mouseButton, modifiers), 0);
		}
	}

	// ---------------------------------------------------------------- feel

	private void rumbleOnDamage(final @Nullable LocalPlayer player, final Gamepad pad) {
		if (player == null || !CouchConfig.get().rumble) {
			this.lastHurtTime = 0;
			return;
		}

		if (player.hurtTime > this.lastHurtTime) {
			pad.rumble(0.45F, 0.75F, 180);
		}

		this.lastHurtTime = player.hurtTime;
	}

	/** Cursor position in GUI pixels, for drawing a guest's cursor. */
	public double cursorGuiX(final Window window) {
		return this.cursorX * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
	}

	public double cursorGuiY(final Window window) {
		return this.cursorY * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());
	}

	public boolean hasCursor() {
		return this.cursorScreen != null;
	}
}
