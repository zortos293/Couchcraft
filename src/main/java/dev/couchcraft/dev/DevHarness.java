package dev.couchcraft.dev;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.couchcraft.CouchConfig;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.Layouts;
import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.render.Compositor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import dev.couchcraft.input.Gamepad;
import org.lwjgl.sdl.SDLGamepad;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Development-only automation, inert unless {@code -Dcouchcraft.devWorld} is set. Opens (or creates) a test world,
 * plays a short scripted couch session and writes captures of the composited frame to {@code couchcraft-dev/}.
 */
public final class DevHarness {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final @Nullable String WORLD = System.getProperty("couchcraft.devWorld");
	private static final boolean SCRIPT = Boolean.getBoolean("couchcraft.devScript");
	private static boolean worldRequested;
	private static long inWorldSince = -1L;
	private static int step;
	private static @Nullable String pendingCapture;

	private DevHarness() {
	}

	private record Step(long atMillis, String label, Runnable action) {
	}

	private static final List<Step> STEPS = new ArrayList<>();

	private static final DevPad PAD = new DevPad("Virtual Pad A");
	private static final DevPad PAD_P1 = new DevPad("Virtual Pad B");
	private static final DevPad PAD_P1_SWITCH = new DevPad("Virtual Pad C");
	private static double markX;
	private static double markZ;
	private static float markYaw;
	private static int slotBefore;

	static {
		STEPS.add(new Step(1_000L, "attach virtual pad", PAD::attach));
		STEPS.add(new Step(5_000L, "pad presses START", () -> PAD.button(Gamepad.START, true)));
		STEPS.add(new Step(5_300L, "pad releases START", () -> PAD.button(Gamepad.START, false)));
		STEPS.add(new Step(14_000L, "capture 2p-stacked", () -> capture("01-2p-stacked")));
		STEPS.add(new Step(14_200L, "lift Player2 into open air", () -> serverCommand("execute as Player2 at @s run tp @s ~ 200 ~")));
		STEPS.add(new Step(14_500L, "pad walks forward (left stick)", () -> {
			mark();
			PAD.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY, -1.0F);
		}));
		STEPS.add(new Step(16_000L, "pad stops walking", () -> {
			PAD.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY, 0.0F);
			reportMovement();
		}));
		STEPS.add(new Step(16_500L, "pad looks right (right stick)", () -> {
			mark();
			PAD.axis(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX, 0.8F);
		}));
		STEPS.add(new Step(17_500L, "pad stops looking", () -> {
			PAD.axis(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX, 0.0F);
			reportMovement();
		}));
		STEPS.add(new Step(18_000L, "pad presses RB", () -> {
			asGuest(1, minecraft -> slotBefore = minecraft.player.getInventory().getSelectedSlot());
			PAD.button(Gamepad.RB, true);
		}));
		STEPS.add(new Step(18_300L, "pad releases RB", () -> {
			PAD.button(Gamepad.RB, false);
			asGuest(1, minecraft -> LOGGER.info("[Couchcraft/dev] P2 hotbar slot {} -> {} (expected +1)", slotBefore, minecraft.player.getInventory().getSelectedSlot()));
		}));
		STEPS.add(new Step(19_000L, "pad presses Y (inventory)", () -> PAD.button(Gamepad.Y, true)));
		STEPS.add(new Step(19_300L, "pad releases Y", () -> PAD.button(Gamepad.Y, false)));
		STEPS.add(new Step(20_000L, "pad moves cursor", () -> {
			PAD.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, -0.7F);
			PAD.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY, 0.5F);
		}));
		STEPS.add(new Step(20_400L, "pad stops cursor", () -> {
			PAD.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 0.0F);
			PAD.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY, 0.0F);
		}));
		STEPS.add(new Step(21_000L, "capture guest inventory with pad cursor", () -> capture("02-guest-inventory")));
		STEPS.add(new Step(21_500L, "pad presses B (close)", () -> PAD.button(Gamepad.B, true)));
		STEPS.add(new Step(21_800L, "pad releases B", () -> {
			PAD.button(Gamepad.B, false);
			asGuest(1, minecraft -> LOGGER.info("[Couchcraft/dev] P2 screen after B = {} (expected none)", minecraft.gui.screen()));
		}));
		STEPS.add(new Step(22_500L, "layout side by side", () -> {
			CouchConfig.get().twoPlayerLayout = Layouts.TwoPlayer.SIDE_BY_SIDE.name();
			Couch.markLayoutDirty();
		}));
		STEPS.add(new Step(25_000L, "capture 2p side by side", () -> capture("03-2p-side")));
		STEPS.add(new Step(26_000L, "add two keyboard-less guests", () -> {
			CouchConfig.get().twoPlayerLayout = Layouts.TwoPlayer.STACKED.name();
			Couch.addGuest(Minecraft.getInstance(), null);
			Couch.addGuest(Minecraft.getInstance(), null);
		}));
		STEPS.add(new Step(37_000L, "capture 4p quad", () -> capture("04-4p-quad")));
		STEPS.add(new Step(38_000L, "pad holds BACK+START (leave)", () -> {
			PAD.button(Gamepad.BACK, true);
			PAD.button(Gamepad.START, true);
		}));
		STEPS.add(new Step(40_000L, "pad releases BACK+START", () -> {
			PAD.button(Gamepad.BACK, false);
			PAD.button(Gamepad.START, false);
			LOGGER.info("[Couchcraft/dev] Players after leave combo = {} (expected 3)", Couch.contexts().size());
		}));
		STEPS.add(new Step(43_000L, "capture 3p", () -> capture("05-3p")));
		STEPS.add(new Step(44_000L, "host pauses", () -> Minecraft.getInstance().pauseGame(false)));
		STEPS.add(new Step(46_000L, "capture host paused", () -> capture("06-host-paused")));
		STEPS.add(new Step(47_000L, "host resumes", () -> Couch.asPrimary(() -> Minecraft.getInstance().gui.setScreen(null))));
		STEPS.add(new Step(48_000L, "server kills Player3", () -> serverCommand("kill Player3")));
		STEPS.add(new Step(51_000L, "capture guest death screen", () -> capture("07-guest-dead")));
		STEPS.add(new Step(52_000L, "Player3 respawns", () -> asGuest(2, minecraft -> {
			minecraft.player.respawn();
			LOGGER.info("[Couchcraft/dev] Player3 respawn requested; screen was {}", minecraft.gui.screen());
		})));
		STEPS.add(new Step(53_000L, "Player4 goes to the Nether", () -> serverCommand("execute in minecraft:the_nether run tp Player4 0 100 0")));
		STEPS.add(new Step(54_000L, "global chunk rebuild (like F3+A)", () -> Minecraft.getInstance().levelExtractor.allChanged()));
		STEPS.add(new Step(61_000L, "F2 screenshot", () -> net.minecraft.client.Screenshot.grab(Minecraft.getInstance(), false)));
		STEPS.add(new Step(62_000L, "capture guest in Nether", () -> capture("08-guest-nether")));
		STEPS.add(new Step(63_000L, "window resized to 1280x720", () -> Minecraft.getInstance().getWindow().setWindowed(1280, 720)));
		STEPS.add(new Step(67_000L, "capture after resize", () -> capture("09-resized")));
		STEPS.add(new Step(68_000L, "host saves and quits with guests in", () -> {
			Minecraft.getInstance().disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
			LOGGER.info("[Couchcraft/dev] Host at title: players={}, split={}", Couch.contexts().size(), Couch.isSplit());
			worldRequested = false;
			inWorldSince = -1L;
			step = 0;
			STEPS.clear();
			STEPS.add(new Step(3_000L, "rejoin: pad presses START", () -> PAD.button(Gamepad.START, true)));
			STEPS.add(new Step(3_300L, "rejoin: pad releases START", () -> PAD.button(Gamepad.START, false)));
			STEPS.add(new Step(13_000L, "capture rejoined", () -> capture("10-rejoined")));
			STEPS.add(new Step(14_000L, "second pad attaches", PAD_P1::attach));
			STEPS.add(new Step(15_000L, "second pad presses BACK (claim P1)", () -> PAD_P1.button(Gamepad.BACK, true)));
			STEPS.add(new Step(15_300L, "second pad releases BACK", () -> {
				PAD_P1.button(Gamepad.BACK, false);
				LOGGER.info("[Couchcraft/dev] P1 pad = {} (expected the second virtual pad)", Couch.primary().pad == null ? "none" : Couch.primary().pad.name());
			}));
			STEPS.add(new Step(16_000L, "P1 pad walks", () -> {
				markPrimary();
				PAD_P1.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 1.0F);
			}));
			STEPS.add(new Step(17_000L, "P1 pad stops", () -> {
				PAD_P1.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 0.0F);
				Minecraft minecraft = Minecraft.getInstance();
				LOGGER.info("[Couchcraft/dev] P1 strafed {} blocks", String.format("%.2f", Math.hypot(minecraft.player.getX() - markX, minecraft.player.getZ() - markZ)));
			}));
			STEPS.add(new Step(17_500L, "P1 pad presses Y (inventory)", () -> PAD_P1.button(Gamepad.Y, true)));
			STEPS.add(new Step(17_800L, "P1 pad releases Y", () -> PAD_P1.button(Gamepad.Y, false)));
			STEPS.add(new Step(18_500L, "P1 pad moves the real cursor", () -> PAD_P1.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 0.8F)));
			STEPS.add(new Step(18_900L, "P1 pad stops cursor", () -> PAD_P1.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 0.0F)));
			STEPS.add(new Step(19_500L, "capture P1 menu on a pad", () -> capture("11-p1-pad-menu")));
			STEPS.add(new Step(20_000L, "P1 pad presses B", () -> PAD_P1.button(Gamepad.B, true)));
			STEPS.add(new Step(20_300L, "P1 pad releases B", () -> {
				PAD_P1.button(Gamepad.B, false);
				LOGGER.info("[Couchcraft/dev] P1 screen after B = {} (expected none)", Minecraft.getInstance().gui.screen());
			}));
			STEPS.add(new Step(21_000L, "third pad attaches", PAD_P1_SWITCH::attach));
			STEPS.add(new Step(22_000L, "third pad presses BACK (P1 switches controllers)", () -> PAD_P1_SWITCH.button(Gamepad.BACK, true)));
			STEPS.add(new Step(22_300L, "third pad releases BACK", () -> {
				PAD_P1_SWITCH.button(Gamepad.BACK, false);
				LOGGER.info("[Couchcraft/dev] P1 pad = {} (expected Virtual Pad C)", Couch.primary().pad == null ? "none" : Couch.primary().pad.name());
				for (var pad : dev.couchcraft.input.Gamepads.connected()) {
					LOGGER.info("[Couchcraft/dev]   pad '{}' owner = {}", pad.name(), pad.owner() == null ? "free" : pad.owner().name);
				}
			}));
			STEPS.add(new Step(23_000L, "capture P1 switch badge", () -> capture("12-p1-switched")));
			STEPS.add(new Step(23_500L, "lift P1 into open air", () -> serverCommand("execute as " + Minecraft.getInstance().getUser().getName() + " at @s run tp @s ~ 200 ~")));
			STEPS.add(new Step(24_000L, "P1 walks on the new pad", () -> {
				markPrimary();
				PAD_P1_SWITCH.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 1.0F);
			}));
			STEPS.add(new Step(25_000L, "P1 stops", () -> {
				PAD_P1_SWITCH.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 0.0F);
				Minecraft minecraft = Minecraft.getInstance();
				LOGGER.info("[Couchcraft/dev] P1 strafed {} blocks on the new pad", String.format("%.2f", Math.hypot(minecraft.player.getX() - markX, minecraft.player.getZ() - markZ)));
			}));
			STEPS.add(new Step(28_000L, "old pad (B) tries to move P1", () -> {
				markPrimary();
				PAD_P1.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 1.0F);
			}));
			STEPS.add(new Step(29_000L, "old pad stops", () -> {
				PAD_P1.axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, 0.0F);
				Minecraft minecraft = Minecraft.getInstance();
				LOGGER.info("[Couchcraft/dev] Old pad moved P1 {} blocks (expected 0.00)", String.format("%.2f", Math.hypot(minecraft.player.getX() - markX, minecraft.player.getZ() - markZ)));
			}));
			STEPS.add(new Step(30_000L, "P1 opens Couch Co-op settings (Ctrl+Shift+C)", () ->
				Minecraft.getInstance().gui.setScreen(new dev.couchcraft.gui.CouchScreen(null))));
			STEPS.add(new Step(31_000L, "P2's controller vibrates (identify)", () -> {
				PlayerContext p2 = slot(1);
				p2.pad.identify();
				LOGGER.info("[Couchcraft/dev] P2 pad identifying = {} (expected true)", p2.pad.isIdentifying());
			}));
			STEPS.add(new Step(31_400L, "capture settings screen", () -> capture("13-settings")));
			STEPS.add(new Step(32_000L, "P1 takes P2's controller (swap)", () -> {
				var p1Before = Couch.primary().pad;
				var p2Before = slot(1).pad;
				Couch.assignPad(Couch.primary(), p2Before);
				LOGGER.info(
					"[Couchcraft/dev] Swap: P1 has P2's old pad = {}, P2 has P1's old pad = {} (expected true, true)",
					Couch.primary().pad == p2Before, slot(1).pad == p1Before
				);
			}));
			STEPS.add(new Step(33_000L, "P1 switches to keyboard only (None)", () -> {
				var freed = Couch.primary().pad;
				Couch.assignPad(Couch.primary(), null);
				LOGGER.info("[Couchcraft/dev] P1 pad = {}, freed pad owner = {} (expected none, free)", Couch.primary().pad, freed.owner());
			}));
			STEPS.add(new Step(34_000L, "capture settings after changes", () -> capture("14-settings-after")));
			STEPS.add(new Step(35_000L, "close settings, P2 opens pause menu", () -> {
				Minecraft.getInstance().gui.setScreen(null);
				asGuest(1, minecraft -> minecraft.pauseGame(false));
			}));
			STEPS.add(new Step(36_000L, "capture guest pause menu", () -> capture("15-guest-pause")));
			STEPS.add(new Step(37_000L, "done", () -> LOGGER.info("[Couchcraft/dev] Script complete")));
		}));
	}

	private static void serverCommand(final String command) {
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
		}
	}

	private static void mark() {
		asGuest(1, minecraft -> {
			markX = minecraft.player.getX();
			markZ = minecraft.player.getZ();
			markYaw = minecraft.player.getYRot();
		});
	}

	private static PlayerContext slot(final int slot) {
		for (PlayerContext context : Couch.contexts()) {
			if (context.slot == slot) {
				return context;
			}
		}

		throw new IllegalStateException("No player in slot " + slot);
	}

	private static void markPrimary() {
		Minecraft minecraft = Minecraft.getInstance();
		markX = minecraft.player.getX();
		markZ = minecraft.player.getZ();
	}

	private static void reportMovement() {
		asGuest(1, minecraft -> LOGGER.info(
			"[Couchcraft/dev] P2 moved {} blocks, turned {} degrees",
			String.format("%.2f", Math.hypot(minecraft.player.getX() - markX, minecraft.player.getZ() - markZ)),
			String.format("%.1f", minecraft.player.getYRot() - markYaw)
		));
	}

	@FunctionalInterface
	private interface GuestAction {
		void run(Minecraft minecraft);
	}

	private static void asGuest(final int slot, final GuestAction action) {
		for (PlayerContext context : Couch.contexts()) {
			if (context.slot == slot) {
				try (Couch.Scope ignored = Couch.enter(context)) {
					action.run(Minecraft.getInstance());
				}
			}
		}
	}

	/** Called at the start of every frame, in the resting state. */
	public static void frame(final Minecraft minecraft) {
		if (WORLD == null) {
			return;
		}

		// Any menu will do (first launch shows accessibility onboarding instead of the title screen).
		if (!worldRequested && minecraft.level == null && minecraft.gui.overlay() == null && minecraft.gui.screen() != null) {
			worldRequested = true;
			openOrCreate(minecraft, WORLD);
			return;
		}

		if (!SCRIPT || minecraft.player == null) {
			return;
		}

		if (inWorldSince < 0L) {
			if (minecraft.gui.screen() == null) {
				inWorldSince = Util.getMillis();
				LOGGER.info("[Couchcraft/dev] Host in world, starting script");
			}

			return;
		}

		long elapsed = Util.getMillis() - inWorldSince;
		while (step < STEPS.size() && elapsed >= STEPS.get(step).atMillis()) {
			Step current = STEPS.get(step++);
			LOGGER.info("[Couchcraft/dev] t={}s {}", elapsed / 1000, current.label());
			current.action().run();
			if (inWorldSince < 0L) {
				// The step started a new phase (left the world); its clock starts when the host is back in.
				break;
			}
		}
	}

	private static void openOrCreate(final Minecraft minecraft, final String name) {
		if (minecraft.getLevelSource().levelExists(name)) {
			LOGGER.info("[Couchcraft/dev] Opening test world '{}'", name);
			minecraft.createWorldOpenFlows().openWorld(name, () -> minecraft.gui.setScreen(new TitleScreen()));
		} else {
			LOGGER.info("[Couchcraft/dev] Creating test world '{}'", name);
			LevelSettings settings = new LevelSettings(
				name, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT
			);
			minecraft.createWorldOpenFlows()
				.createFreshLevel(name, settings, WorldOptions.defaultWithRandomSeed(), WorldPresets::createNormalWorldDimensions, new TitleScreen());
		}
	}

	private static void capture(final String label) {
		pendingCapture = label;
	}

	/** Called after the composite has been built for this frame. */
	public static void afterComposite() {
		if (pendingCapture == null) {
			return;
		}

		String label = pendingCapture;
		pendingCapture = null;
		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget target = Couch.isSplit() ? Compositor.target() : minecraft.gameRenderer.mainRenderTarget();
		if (target == null) {
			LOGGER.warn("[Couchcraft/dev] Nothing to capture for {}", label);
			return;
		}

		logState(label);
		Path directory = minecraft.gameDirectory.toPath().resolve("couchcraft-dev");
		Screenshot.takeScreenshot(target, image -> Util.ioPool().execute(() -> write(image, directory.resolve(label + ".png"))));
	}

	private static void write(final NativeImage image, final Path path) {
		try (image) {
			Files.createDirectories(path.getParent());
			image.writeToFile(path);
			LOGGER.info("[Couchcraft/dev] Captured {}", path);
		} catch (IOException e) {
			LOGGER.warn("[Couchcraft/dev] Capture failed", e);
		}
	}

	private static void logState(final String label) {
		LOGGER.info(
			"[Couchcraft/dev] State at {}: {} players, paused={}, fps={}, sound: {}", label, Couch.contexts().size(), Minecraft.getInstance().isPaused(), Minecraft.getInstance().getFps(), dev.couchcraft.sound.SoundRouter.stats()
		);
		for (PlayerContext context : Couch.contexts()) {
			LocalPlayer player = context.player();
			LOGGER.info(
				"[Couchcraft/dev]   P{} {} viewport={} frame={} target={}x{} player={} screen={}",
				context.slot + 1,
				context.name,
				context.viewport(),
				context.frame(),
				context.gameRenderer.mainRenderTarget().width,
				context.gameRenderer.mainRenderTarget().height,
				player == null ? "none" : player.level().dimension().identifier() + " " + player.blockPosition().toShortString(),
				context.gui.screen() == null ? "none" : context.gui.screen().getClass().getSimpleName()
			);
		}
	}
}
