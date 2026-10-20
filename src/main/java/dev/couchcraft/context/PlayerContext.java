package dev.couchcraft.context;

import dev.couchcraft.input.Gamepad;
import dev.couchcraft.input.PadController;
import dev.couchcraft.mixin.access.CloudRendererInvoker;
import dev.couchcraft.mixin.access.HudAccessor;
import dev.couchcraft.mixin.access.MinecraftAccessor;
import dev.couchcraft.mixin.access.ParticleEngineAccessor;
import dev.couchcraft.profile.Profile;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.network.Connection;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Everything that vanilla keeps as "the" player on the {@link Minecraft} singleton, for one couch player.
 * Slot 0 is the primary player and owns vanilla's original instances; guests get their own.
 */
public final class PlayerContext {
	public static final int[] COLORS = {0xFFFFC400, 0xFF2F5BFF, 0xFFE5372B, 0xFF16A05A};

	public final int slot;
	public final boolean primary;
	/** The player's name; for a guest it is set when they pick a profile (until then "Player N"). */
	public String name;
	public UUID profileId;
	/** The saved guest profile this player is using; null for the host and for a guest still choosing. */
	public @Nullable Profile profile;

	public final GameRenderer gameRenderer;
	public final LevelRenderer levelRenderer;
	public final LevelExtractor levelExtractor;
	public final ParticleEngine particleEngine;
	public final Gui gui;
	public final MouseHandler mouseHandler;

	private @Nullable LocalPlayer player;
	private @Nullable ClientLevel level;
	private @Nullable MultiPlayerGameMode gameMode;
	private @Nullable Entity crosshairPickEntity;
	private @Nullable HitResult hitResult;
	private @Nullable Connection pendingConnection;
	private int rightClickDelay;
	private int missTime;
	private CameraType cameraType = CameraType.FIRST_PERSON;
	private boolean smoothCamera;
	final KeyState keys = new KeyState();

	Viewport viewport = new Viewport(0, 0, 1, 1);
	VirtualWindow.@Nullable Frame frame;

	public @Nullable Gamepad pad;
	public final PadController controller;
	@Nullable Connection connection;
	public final long createdAtMillis = System.currentTimeMillis();
	/** When the name badge was last (re)shown: on joining and whenever this player picks up a controller. */
	public long badgeSince = this.createdAtMillis;
	boolean removed;

	private PlayerContext(
		final int slot,
		final String name,
		final UUID profileId,
		final GameRenderer gameRenderer,
		final LevelRenderer levelRenderer,
		final LevelExtractor levelExtractor,
		final ParticleEngine particleEngine,
		final Gui gui,
		final MouseHandler mouseHandler
	) {
		this.slot = slot;
		this.primary = slot == 0;
		this.name = name;
		this.profileId = profileId;
		this.gameRenderer = gameRenderer;
		this.levelRenderer = levelRenderer;
		this.levelExtractor = levelExtractor;
		this.particleEngine = particleEngine;
		this.gui = gui;
		this.mouseHandler = mouseHandler;
		this.controller = new PadController(this);
	}

	/** Wraps vanilla's own instances. The live state is still on the singleton, so nothing is copied. */
	static PlayerContext primary(final Minecraft minecraft) {
		PlayerContext context = new PlayerContext(
			0,
			minecraft.getUser().getName(),
			minecraft.getUser().getProfileId(),
			minecraft.gameRenderer,
			minecraft.levelRenderer,
			minecraft.levelExtractor,
			minecraft.particleEngine,
			minecraft.gui,
			minecraft.mouseHandler
		);
		context.cameraType = minecraft.options.getCameraType();
		// The host doesn't "join"; their badge only appears when they pick up a controller.
		context.badgeSince = Long.MIN_VALUE / 2;
		return context;
	}

	/** Builds a guest's private engines. Must run on the render thread, inside a virtual window of the guest's size. */
	static PlayerContext guest(final Minecraft minecraft, final int slot, final String name, final UUID profileId) {
		GameRenderer gameRenderer = new GameRenderer(
			minecraft, new FirstPersonHandsAndItemsRenderer(minecraft), minecraft.getModelManager(), minecraft.getItemModelResolver()
		);
		LevelRenderer levelRenderer = new LevelRenderer(
			minecraft.getEntityRenderDispatcher(),
			minecraft.getBlockEntityRenderDispatcher(),
			minecraft.getModelManager(),
			minecraft.getTextureManager(),
			minecraft.getAtlasManager(),
			minecraft.getShaderManager(),
			gameRenderer,
			minecraft.getWindow().getWidth(),
			minecraft.getWindow().getHeight()
		);
		LevelExtractor levelExtractor = new LevelExtractor(minecraft, gameRenderer.gameRenderState().levelRenderState, levelRenderer);
		ParticleEngine particleEngine = new ParticleEngine(null, ((ParticleEngineAccessor)minecraft.particleEngine).couchcraft$resources());
		Hud hud = new Hud(minecraft);
		((HudAccessor)hud).couchcraft$setWaypointStyles(((HudAccessor)minecraft.gui.hud).couchcraft$waypointStyles());
		Gui gui = new Gui(minecraft, hud, gameRenderer.gameRenderState().guiRenderState);
		PlayerContext context = new PlayerContext(
			slot, name, profileId, gameRenderer, levelRenderer, levelExtractor, particleEngine, gui, new MouseHandler(minecraft)
		);
		context.reloadResources(minecraft);
		return context;
	}

	/** Guests are not registered with the resource manager; mirror the reload work vanilla does for the primary. */
	void reloadResources(final Minecraft minecraft) {
		if (this.primary) {
			return;
		}

		CloudRendererInvoker clouds = (CloudRendererInvoker)this.levelRenderer.cloudRenderer();
		clouds.couchcraft$apply(clouds.couchcraft$prepare(minecraft.getResourceManager(), Profiler.get()), minecraft.getResourceManager(), Profiler.get());
		this.gameRenderer.onResourceManagerReload(minecraft.getResourceManager());
		this.levelExtractor.onResourceManagerReload(minecraft.getResourceManager());
		this.particleEngine.clearParticles();
	}

	void saveFrom(final Minecraft minecraft) {
		MinecraftAccessor access = (MinecraftAccessor)minecraft;
		this.player = minecraft.player;
		this.level = minecraft.level;
		this.gameMode = minecraft.gameMode;
		this.crosshairPickEntity = minecraft.crosshairPickEntity;
		this.hitResult = minecraft.hitResult;
		this.pendingConnection = access.couchcraft$pendingConnection();
		this.rightClickDelay = access.couchcraft$rightClickDelay();
		this.missTime = access.couchcraft$missTime();
		this.cameraType = minecraft.options.getCameraType();
		this.smoothCamera = minecraft.options.smoothCamera;
		this.keys.saveFromLive();
	}

	void loadInto(final Minecraft minecraft) {
		MinecraftAccessor access = (MinecraftAccessor)minecraft;
		minecraft.player = this.player;
		minecraft.level = this.level;
		minecraft.gameMode = this.gameMode;
		minecraft.crosshairPickEntity = this.crosshairPickEntity;
		minecraft.hitResult = this.hitResult;
		access.couchcraft$setPendingConnection(this.pendingConnection);
		access.couchcraft$setRightClickDelay(this.rightClickDelay);
		access.couchcraft$setMissTime(this.missTime);
		access.couchcraft$setGameRenderer(this.gameRenderer);
		access.couchcraft$setLevelRenderer(this.levelRenderer);
		access.couchcraft$setLevelExtractor(this.levelExtractor);
		access.couchcraft$setParticleEngine(this.particleEngine);
		access.couchcraft$setGui(this.gui);
		access.couchcraft$setMouseHandler(this.mouseHandler);
		minecraft.options.setCameraType(this.cameraType);
		minecraft.options.smoothCamera = this.smoothCamera;
		this.keys.loadIntoLive();
	}

	/** Frees GPU resources. Only for guests, after their state has been unloaded from the singleton. */
	void destroy() {
		if (this.primary) {
			return;
		}

		this.levelRenderer.close();
		this.gameRenderer.close();
	}

	public Viewport viewport() {
		return this.viewport;
	}

	public VirtualWindow.@Nullable Frame frame() {
		return this.frame;
	}

	public int color() {
		return COLORS[this.slot % COLORS.length];
	}

	public boolean isRemoved() {
		return this.removed;
	}

	public @Nullable Connection connection() {
		return this.connection;
	}

	/** The player entity, whether or not this context is currently live. */
	public @Nullable LocalPlayer player() {
		return Couch.current() == this ? Minecraft.getInstance().player : this.player;
	}
}
