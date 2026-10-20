package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.dev.DevHarness;
import dev.couchcraft.mixin.access.MinecraftAccessor;
import dev.couchcraft.render.Compositor;
import dev.couchcraft.CouchReloadListener;
import dev.couchcraft.sound.SoundRouter;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame loop. Ticks, input and the update/extract/render phases run once per player inside that player's scope;
 * world sound, music and textures stay single. With one player every hook passes straight through.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	@Final
	private ReloadableResourceManager resourceManager;

	@Shadow
	@Final
	private Window window;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void couchcraft$registerReloadListener(final GameConfig gameConfig, final CallbackInfo ci) {
		this.resourceManager.registerReloadListener(new CouchReloadListener());
	}

	// ---------------------------------------------------------------- runTick

	@Inject(method = "runTick", at = @At("HEAD"))
	private void couchcraft$frameStart(final boolean advanceGameTime, final CallbackInfo ci) {
		Couch.frameStart((Minecraft)(Object)this);
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
	private void couchcraft$runTasksAsPrimary(final Minecraft instance, final Operation<Void> original) {
		Couch.asPrimary(() -> original.call(instance));
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"))
	private void couchcraft$tickEveryPlayer(final Minecraft instance, final Operation<Void> original) {
		Couch.forEachPlayer(() -> original.call(instance));
	}

	@WrapOperation(
		method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;updateSource(Lnet/minecraft/client/Camera;)V")
	)
	private void couchcraft$updateEars(final SoundManager instance, final Camera camera, final Operation<Void> original) {
		if (SoundRouter.active()) {
			SoundRouter.updateEars();
		}

		original.call(instance, camera);
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;handleAccumulatedMovement()V"))
	private void couchcraft$mouseEveryPlayer(final MouseHandler instance, final Operation<Void> original) {
		if (!Couch.isSplit()) {
			original.call(instance);
			return;
		}

		Couch.forEachPlayer(() -> original.call(Minecraft.getInstance().mouseHandler));
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;isPausing()Z"))
	private boolean couchcraft$pauseIsAVote(final Gui instance, final Operation<Boolean> original) {
		return Couch.isSplit() ? Couch.allPausing() : original.call(instance);
	}

	// ---------------------------------------------------------------- renderFrame

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pauseIfInactive()V"))
	private void couchcraft$noFocusPauseOnTheCouch(final Minecraft instance, final Operation<Void> original) {
		// Couch players look at the TV, not the window focus: no auto-pause while split or while P1 plays on a pad.
		if (!Couch.isSplit() && (!Couch.ready() || Couch.primary().pad == null)) {
			original.call(instance);
		}
	}

	/** Holding attack on a pad keeps mining even if the OS mouse isn't captured (e.g. the window lost focus). */
	@WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
	private boolean couchcraft$padsCanMine(final MouseHandler instance, final Operation<Boolean> original) {
		PlayerContext current = Couch.current();
		return original.call(instance) || current != null && current.pad != null;
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;update()V"))
	private void couchcraft$guiUpdateAsPrimary(final Gui instance, final Operation<Void> original) {
		Couch.asPrimary(() -> original.call(instance));
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;update()V"))
	private void couchcraft$levelUpdateAsPrimary(final ClientLevel instance, final Operation<Void> original) {
		Couch.asPrimary(() -> original.call(instance));
	}

	@WrapOperation(
		method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;update(Lnet/minecraft/client/DeltaTracker;)V")
	)
	private void couchcraft$cameraUpdateAsPrimary(final GameRenderer instance, final DeltaTracker deltaTracker, final Operation<Void> original) {
		Couch.asPrimary(() -> original.call(instance, deltaTracker));
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pick(F)V"))
	private void couchcraft$pickAsPrimary(final Minecraft instance, final float partialTicks, final Operation<Void> original) {
		Couch.asPrimary(() -> original.call(instance, partialTicks));
	}

	@WrapOperation(
		method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;extract(Lnet/minecraft/client/DeltaTracker;Z)V")
	)
	private void couchcraft$extractEveryPlayer(
		final GameRenderer instance, final DeltaTracker deltaTracker, final boolean advanceGameTime, final Operation<Void> original
	) {
		Couch.asPrimary(() -> original.call(instance, deltaTracker, advanceGameTime));
		if (!Couch.isSplit()) {
			return;
		}

		int framerateLimit = instance.gameRenderState().framerateLimit;
		Couch.forEachGuest(() -> {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.gui.update();
			if (minecraft.isGameLoadFinished() && advanceGameTime && minecraft.level != null) {
				minecraft.level.update();
			}

			minecraft.gameRenderer.update(deltaTracker);
			((MinecraftAccessor)minecraft).couchcraft$pick(deltaTracker.getGameTimeDeltaPartialTick(false));
			minecraft.gameRenderer.gameRenderState().framerateLimit = framerateLimit;
			original.call(minecraft.gameRenderer, deltaTracker, advanceGameTime);
		});
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V"))
	private void couchcraft$renderEveryPlayer(final GameRenderer instance, final Operation<Void> original) {
		Couch.asPrimary(() -> original.call(instance));
		if (Couch.isSplit()) {
			Couch.forEachGuest(() -> original.call(Minecraft.getInstance().gameRenderer));
			Compositor.composite(Couch.contexts(), this.window.getWidth(), this.window.getHeight());
		}

		DevHarness.afterComposite();
	}

	@WrapOperation(
		method = "renderFrame",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"
		)
	)
	private void couchcraft$presentComposite(
		final GpuSurface instance, final CommandEncoder encoder, final GpuTextureView texture, final Operation<Void> original
	) {
		GpuTextureView composite = Couch.isSplit() ? Compositor.view() : null;
		original.call(instance, encoder, composite != null ? composite : texture);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;endFrame()V"))
	private void couchcraft$endFrameEveryPlayer(final LevelRenderer instance, final Operation<Void> original) {
		original.call(instance);
		if (Couch.isSplit()) {
			for (PlayerContext context : Couch.contexts()) {
				if (!context.primary) {
					original.call(context.levelRenderer);
				}
			}
		}
	}

	// ---------------------------------------------------------------- tick: things that exist once

	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/MusicManager;tick()V"))
	private boolean couchcraft$musicOnce(final MusicManager instance) {
		return !Couch.isGuestActive();
	}

	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;tick(Z)V"))
	private boolean couchcraft$soundOnce(final SoundManager instance, final boolean paused) {
		return !Couch.isGuestActive();
	}

	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyboardHandler;tick()V"))
	private boolean couchcraft$keyboardOnce(final KeyboardHandler instance) {
		return !Couch.isGuestActive();
	}

	@WrapWithCondition(
		method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;stop()V")
	)
	private boolean couchcraft$guestsDontSilenceEveryone(final SoundManager instance) {
		return !Couch.isGuestActive();
	}

	// ---------------------------------------------------------------- leaving

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guestLeavesInsteadOfQuitting(final Screen screen, final boolean keepResourcePacks, final boolean stopSound, final CallbackInfo ci) {
		if (!Couch.ready()) {
			return;
		}

		if (Couch.isGuestActive()) {
			Couch.requestRemove(Couch.current());
			ci.cancel();
		} else if (Couch.isSplit()) {
			Couch.removeAllGuests((Minecraft)(Object)this);
		}
	}

	@Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guestSaveAndQuit(final Component message, final CallbackInfo ci) {
		if (Couch.ready() && Couch.isGuestActive()) {
			Couch.requestRemove(Couch.current());
			ci.cancel();
		}
	}

	@Inject(method = "resizeGui", at = @At("TAIL"))
	private void couchcraft$relayoutOnResize(final CallbackInfo ci) {
		Couch.markLayoutDirty();
	}
}
