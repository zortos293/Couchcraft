package dev.couchcraft.context;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.couchcraft.CouchConfig;
import dev.couchcraft.dev.DevHarness;
import dev.couchcraft.gui.CouchScreen;
import dev.couchcraft.input.Gamepad;
import dev.couchcraft.input.Gamepads;
import dev.couchcraft.net.GuestConnector;
import dev.couchcraft.render.Compositor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Owns every couch player and decides whose state is live on the {@link Minecraft} singleton.
 * <p>
 * The resting state, outside of any {@link #enter} scope, is always the primary player with no virtual window,
 * which is exactly vanilla. Everything Couchcraft does happens inside short, nested scopes.
 */
public final class Couch {
	public static final int MAX_PLAYERS = 4;
	private static final Logger LOGGER = LogUtils.getLogger();

	private static final List<PlayerContext> CONTEXTS = new ArrayList<>();
	private static final Map<Connection, PlayerContext> BY_CONNECTION = new IdentityHashMap<>();
	private static final Set<Connection> GUEST_CONNECTIONS = Collections.newSetFromMap(new WeakHashMap<>());
	/** Lower-case names of guests that are, or may still be, logged in. Read from the server thread. */
	private static final Set<String> GUEST_NAMES = ConcurrentHashMap.newKeySet();
	private static final List<PlayerContext> PENDING_REMOVALS = new ArrayList<>();

	private static @Nullable PlayerContext current;
	private static boolean layoutDirty = true;
	private static int lastWidth = -1;
	private static int lastHeight = -1;
	private static int lastGuiScaleOption = -1;

	private Couch() {
	}

	// ---------------------------------------------------------------- lifecycle

	public static boolean ready() {
		return !CONTEXTS.isEmpty();
	}

	/** Called at the top of every frame, in the resting state. */
	public static void frameStart(final Minecraft minecraft) {
		if (!ready()) {
			if (!minecraft.isGameLoadFinished()) {
				return;
			}

			PlayerContext primary = PlayerContext.primary(minecraft);
			CONTEXTS.add(primary);
			current = primary;
			LOGGER.info("[Couchcraft] Ready. Press START on a controller in a singleplayer world to join.");
		}

		if (!PENDING_REMOVALS.isEmpty()) {
			for (PlayerContext context : List.copyOf(PENDING_REMOVALS)) {
				removeGuestNow(minecraft, context);
			}

			PENDING_REMOVALS.clear();
		}

		DevHarness.frame(minecraft);
		Gamepads.poll();
		handleControllerJoins(minecraft);
		handleKeyboardShortcuts(minecraft);
		handleAutoGuests(minecraft);
		updateLayout(minecraft);

		for (PlayerContext context : List.copyOf(CONTEXTS)) {
			if (!context.removed) {
				try (Scope ignored = enter(context)) {
					context.controller.frame(minecraft);
				}
			}
		}
	}

	private static void handleControllerJoins(final Minecraft minecraft) {
		PlayerContext primary = primary();
		for (Gamepad pad : Gamepads.connected()) {
			if (pad.isActive()) {
				// Vanilla only counts keyboard/mouse as activity and throttles to 30 (then 10) FPS when "AFK".
				minecraft.getFramerateLimitTracker().onInputReceived();
			}

			if (pad.owner() != null) {
				continue;
			}

			boolean inWorld = minecraft.level != null && minecraft.player != null;
			if (pad.pressed(Gamepad.START) && inWorld && canAddGuest(minecraft)) {
				PlayerContext padless = firstPadlessGuest();
				if (padless != null) {
					attachPad(padless, pad);
				} else {
					addGuest(minecraft, pad);
				}
			} else if (pad.pressed(Gamepad.BACK)) {
				// BACK on any free pad hands it to P1, also when P1 already has one: P1 switches controllers and the
				// old pad becomes free. The keyboard and mouse keep working for P1 either way.
				attachPad(primary, pad);
			} else if (primary.pad == null && pad.anyPressed() && (!inWorld || !minecraft.hasSingleplayerServer())) {
				// Outside a couch world (title screen, remote server) any button claims a pad for a padless P1.
				attachPad(primary, pad);
			}
		}

		for (PlayerContext context : CONTEXTS) {
			if (context.pad != null && !context.pad.isConnected()) {
				LOGGER.info("[Couchcraft] Controller for {} disconnected", context.name);
				context.pad.release();
				context.pad = null;
			}
		}
	}

	private static int autoGuestsRemaining = Integer.getInteger("couchcraft.autoGuests", 0);

	/** Development aid: {@code -Dcouchcraft.autoGuests=N} adds N guests once the host has spawned. */
	private static void handleAutoGuests(final Minecraft minecraft) {
		if (autoGuestsRemaining > 0 && minecraft.player != null && minecraft.gui.screen() == null && canAddGuest(minecraft)) {
			autoGuestsRemaining--;
			addGuest(minecraft, null);
		}
	}

	private static final boolean[] SHORTCUT_WAS_DOWN = new boolean[4];

	/**
	 * Ctrl+Shift+J adds a player (they can pick up a controller later with START), Ctrl+Shift+K removes the last one,
	 * Ctrl+Shift+L flips the two-player layout between stacked and side by side, Ctrl+Shift+C opens the controller
	 * settings (players, controllers, vibrate-to-identify).
	 */
	private static void handleKeyboardShortcuts(final Minecraft minecraft) {
		boolean modifiers = minecraft.isWindowActive()
			&& InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)
			&& InputConstants.isKeyDown(InputConstants.KEY_LSHIFT);
		int[] keys = {InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L, InputConstants.KEY_C};
		for (int i = 0; i < keys.length; i++) {
			boolean down = modifiers && InputConstants.isKeyDown(keys[i]);
			boolean pressed = down && !SHORTCUT_WAS_DOWN[i];
			SHORTCUT_WAS_DOWN[i] = down;
			if (!pressed) {
				continue;
			}

			switch (i) {
				case 0 -> addGuest(minecraft, null);
				case 1 -> {
					PlayerContext last = CONTEXTS.getLast();
					if (!last.primary) {
						requestRemove(last);
					}
				}
				case 3 -> {
					Screen open = minecraft.gui.screen();
					if (open instanceof CouchScreen couchScreen) {
						couchScreen.onClose();
					} else {
						minecraft.gui.setScreen(new CouchScreen(open));
					}
				}
				default -> {
					CouchConfig config = CouchConfig.get();
					config.twoPlayerLayout = (config.twoPlayerLayout() == Layouts.TwoPlayer.STACKED ? Layouts.TwoPlayer.SIDE_BY_SIDE : Layouts.TwoPlayer.STACKED).name();
					CouchConfig.save();
					markLayoutDirty();
				}
			}
		}
	}

	private static @Nullable PlayerContext firstPadlessGuest() {
		for (PlayerContext context : CONTEXTS) {
			if (!context.primary && context.pad == null) {
				return context;
			}
		}

		return null;
	}

	public static void attachPad(final PlayerContext context, final Gamepad pad) {
		if (context.pad != null) {
			context.pad.release();
		}

		context.pad = pad;
		pad.claim(context);
		pad.consumeAll();
		context.badgeSince = System.currentTimeMillis();
		LOGGER.info("[Couchcraft] {} is now using controller '{}'", context.name, pad.name());
	}

	/**
	 * Gives {@code pad} to {@code context}, or takes its controller away when {@code pad} is null. If another player
	 * holds that pad, the two players swap: the other player gets {@code context}'s previous controller (or none).
	 */
	public static void assignPad(final PlayerContext context, final @Nullable Gamepad pad) {
		Gamepad previous = context.pad;
		if (pad == previous) {
			return;
		}

		PlayerContext otherOwner = pad == null ? null : pad.owner();
		if (previous != null) {
			previous.release();
			context.pad = null;
		}

		if (otherOwner != null && otherOwner != context) {
			otherOwner.pad = null;
			pad.release();
			if (previous != null) {
				attachPad(otherOwner, previous);
			}
		}

		if (pad != null) {
			attachPad(context, pad);
		} else {
			LOGGER.info("[Couchcraft] {} no longer uses a controller", context.name);
		}
	}

	// ---------------------------------------------------------------- scopes

	public static PlayerContext primary() {
		return CONTEXTS.getFirst();
	}

	public static @Nullable PlayerContext current() {
		return current;
	}

	/** True while a guest's state is live on the singleton. */
	public static boolean isGuestActive() {
		return current != null && !current.primary;
	}

	public static boolean isSplit() {
		return CONTEXTS.size() > 1;
	}

	public static List<PlayerContext> contexts() {
		return Collections.unmodifiableList(CONTEXTS);
	}

	/** Makes {@code context} live on the singleton (and its viewport the window) until the scope closes. Nestable. */
	public static Scope enter(final PlayerContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		PlayerContext previous = current;
		VirtualWindow.Frame previousFrame = VirtualWindow.active();
		if (previous != context) {
			if (previous != null) {
				previous.saveFrom(minecraft);
			}

			context.loadInto(minecraft);
			current = context;
		}

		VirtualWindow.set(isSplit() ? context.frame : null);
		return new Scope(previous, previousFrame);
	}

	public static Scope enterPrimary() {
		return enter(primary());
	}

	public static final class Scope implements AutoCloseable {
		private final @Nullable PlayerContext previous;
		private final VirtualWindow.@Nullable Frame previousFrame;

		private Scope(final @Nullable PlayerContext previous, final VirtualWindow.@Nullable Frame previousFrame) {
			this.previous = previous;
			this.previousFrame = previousFrame;
		}

		@Override
		public void close() {
			Minecraft minecraft = Minecraft.getInstance();
			if (this.previous != null && current != this.previous) {
				current.saveFrom(minecraft);
				this.previous.loadInto(minecraft);
				current = this.previous;
			}

			VirtualWindow.set(this.previousFrame);
		}
	}

	/** Runs {@code action} once per player, each inside their own scope. Without guests it just runs it. */
	public static void forEachPlayer(final Runnable action) {
		if (!isSplit()) {
			action.run();
			return;
		}

		for (PlayerContext context : List.copyOf(CONTEXTS)) {
			if (!context.removed) {
				try (Scope ignored = enter(context)) {
					action.run();
				}
			}
		}
	}

	/** Runs {@code action} for every guest, each inside their own scope. */
	public static void forEachGuest(final Runnable action) {
		for (PlayerContext context : List.copyOf(CONTEXTS)) {
			if (!context.primary && !context.removed) {
				try (Scope ignored = enter(context)) {
					action.run();
				}
			}
		}
	}

	/** Runs {@code action} as the primary player, inside its viewport when split. */
	public static void asPrimary(final Runnable action) {
		if (!isSplit()) {
			action.run();
			return;
		}

		try (Scope ignored = enterPrimary()) {
			action.run();
		}
	}

	public static boolean allPausing() {
		for (PlayerContext context : CONTEXTS) {
			if (!context.gui.isPausing()) {
				return false;
			}
		}

		return true;
	}

	// ---------------------------------------------------------------- layout

	public static void markLayoutDirty() {
		layoutDirty = true;
	}

	private static void updateLayout(final Minecraft minecraft) {
		var window = minecraft.getWindow();
		int width = window.getWidth();
		int height = window.getHeight();
		int guiScaleOption = minecraft.options.guiScale().get();
		if (!layoutDirty && width == lastWidth && height == lastHeight && guiScaleOption == lastGuiScaleOption) {
			return;
		}

		boolean wasSplit = lastWidth >= 0 && CONTEXTS.stream().anyMatch(c -> c.frame != null);
		layoutDirty = false;
		lastWidth = width;
		lastHeight = height;
		lastGuiScaleOption = guiScaleOption;
		if (width <= 0 || height <= 0) {
			return;
		}

		if (!isSplit()) {
			primary().viewport = new Viewport(0, 0, width, height);
			primary().frame = null;
			if (wasSplit) {
				// Back to one player: let vanilla re-measure the real window and the primary's screen.
				minecraft.resizeGui();
			}

			return;
		}

		double screenPerPixelX = (double)window.getScreenWidth() / width;
		double screenPerPixelY = (double)window.getScreenHeight() / height;
		List<Viewport> viewports = Layouts.compute(CONTEXTS.size(), width, height, CouchConfig.get().twoPlayerLayout());

		for (int i = 0; i < CONTEXTS.size(); i++) {
			PlayerContext context = CONTEXTS.get(i);
			Viewport viewport = viewports.get(i);
			int scale = VirtualWindow.Frame.calculateScale(viewport.width(), viewport.height(), guiScaleOption, minecraft.isEnforceUnicode());
			context.viewport = viewport;
			context.frame = VirtualWindow.Frame.of(viewport, screenPerPixelX, screenPerPixelY, scale);
		}

		for (PlayerContext context : CONTEXTS) {
			try (Scope ignored = enter(context)) {
				Screen screen = context.gui.screen();
				if (screen != null) {
					screen.resize(context.frame.guiScaledWidth(), context.frame.guiScaledHeight());
				}

				context.mouseHandler.setIgnoreFirstMove();
			}
		}
	}

	// ---------------------------------------------------------------- guests

	public static boolean canAddGuest(final Minecraft minecraft) {
		return ready() && CONTEXTS.size() < MAX_PLAYERS && minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null;
	}

	private static int freeSlot() {
		for (int slot = 1; slot < MAX_PLAYERS; slot++) {
			final int s = slot;
			if (CONTEXTS.stream().noneMatch(c -> c.slot == s)) {
				return slot;
			}
		}

		return -1;
	}

	public static @Nullable PlayerContext addGuest(final Minecraft minecraft, final @Nullable Gamepad pad) {
		if (!canAddGuest(minecraft) || current != primary()) {
			return null;
		}

		int slot = freeSlot();
		if (slot < 0) {
			return null;
		}

		String name = CouchConfig.get().guestName(slot, minecraft.getUser().getName());
		// Never log in over someone already on the server (a LAN friend with the same name would be kicked).
		var playerList = minecraft.getSingleplayerServer().getPlayerList();
		for (int suffix = 2; playerList.getPlayerByName(name) != null && suffix < 100; suffix++) {
			String base = CouchConfig.get().guestName(slot, minecraft.getUser().getName());
			String tail = "_" + suffix;
			name = base.substring(0, Math.min(base.length(), 16 - tail.length())) + tail;
		}

		UUID profileId = UUIDUtil.createOfflinePlayerUUID(name);

		// Insert in slot order, then lay out so the new player's engines are created at their final size.
		int index = 0;
		while (index < CONTEXTS.size() && CONTEXTS.get(index).slot < slot) {
			index++;
		}

		List<Viewport> viewports = Layouts.compute(CONTEXTS.size() + 1, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), CouchConfig.get().twoPlayerLayout());
		Viewport viewport = viewports.get(index);
		var window = minecraft.getWindow();
		int scale = VirtualWindow.Frame.calculateScale(viewport.width(), viewport.height(), minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
		VirtualWindow.Frame frame = VirtualWindow.Frame.of(
			viewport, (double)window.getScreenWidth() / window.getWidth(), (double)window.getScreenHeight() / window.getHeight(), scale
		);

		PlayerContext guest;
		VirtualWindow.Frame previous = VirtualWindow.active();
		VirtualWindow.set(frame);
		try {
			guest = PlayerContext.guest(minecraft, slot, name, profileId);
		} finally {
			VirtualWindow.set(previous);
		}

		guest.viewport = viewport;
		guest.frame = frame;
		CONTEXTS.add(index, guest);
		GUEST_NAMES.add(name.toLowerCase(Locale.ROOT));
		layoutDirty = true;
		updateLayout(minecraft);
		if (pad != null) {
			attachPad(guest, pad);
		}

		try (Scope ignored = enter(guest)) {
			guest.gui.setScreen(new GenericMessageScreen(Component.translatableWithFallback("couchcraft.joining", "%s is joining…", name)));
		}

		Connection connection = GuestConnector.connect(minecraft, guest);
		guest.connection = connection;
		BY_CONNECTION.put(connection, guest);
		GUEST_CONNECTIONS.add(connection);
		LOGGER.info("[Couchcraft] {} joined as player {}", name, slot + 1);
		return guest;
	}

	/** Schedules a guest to leave at the start of the next frame. Safe to call from inside any scope. */
	public static void requestRemove(final PlayerContext context) {
		if (!context.primary && !PENDING_REMOVALS.contains(context)) {
			PENDING_REMOVALS.add(context);
		}
	}

	public static void removeAllGuests(final Minecraft minecraft) {
		for (PlayerContext context : List.copyOf(CONTEXTS)) {
			if (!context.primary) {
				removeGuestNow(minecraft, context);
			}
		}

		PENDING_REMOVALS.clear();
	}

	private static void removeGuestNow(final Minecraft minecraft, final PlayerContext context) {
		if (context.removed || context.primary) {
			return;
		}

		if (current == context) {
			// Never tear down the live context; it will be handled when the scope unwinds.
			requestRemove(context);
			return;
		}

		LOGGER.info("[Couchcraft] {} left", context.name);
		try (Scope ignored = enter(context)) {
			if (context.connection != null && context.connection.isConnected()) {
				context.connection.disconnect(Component.translatableWithFallback("couchcraft.left", "Left the couch"));
			}

			context.levelExtractor.setLevel(null);
			context.particleEngine.setLevel(null);
			context.gameRenderer.setLevel(null);
			context.gameRenderer.resetData();
			minecraft.player = null;
			minecraft.level = null;
			minecraft.gameMode = null;
			minecraft.hitResult = null;
			minecraft.crosshairPickEntity = null;
		}

		context.removed = true;
		if (context.connection != null) {
			BY_CONNECTION.remove(context.connection);
		}

		CONTEXTS.remove(context);
		GUEST_NAMES.remove(context.name.toLowerCase(Locale.ROOT));
		if (context.pad != null) {
			context.pad.release();
			context.pad = null;
		}

		context.destroy();
		layoutDirty = true;
		updateLayout(minecraft);
		if (!isSplit()) {
			Compositor.release();
		}
	}

	// ---------------------------------------------------------------- lookups

	public static @Nullable PlayerContext byConnection(final @Nullable Connection connection) {
		return connection == null ? null : BY_CONNECTION.get(connection);
	}

	/** True for any connection that ever belonged to a guest, including ones that already left. */
	public static boolean isGuestConnection(final @Nullable Connection connection) {
		return connection != null && GUEST_CONNECTIONS.contains(connection);
	}

	public static boolean isGuestName(final String name) {
		return GUEST_NAMES.contains(name.toLowerCase(Locale.ROOT));
	}

	/** Called when a resource reload finishes, so guests' private renderers pick up the new resources. */
	public static void onResourcesReloaded(final Minecraft minecraft) {
		for (PlayerContext context : CONTEXTS) {
			context.reloadResources(minecraft);
		}
	}
}
