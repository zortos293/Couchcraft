package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Listener;
import dev.couchcraft.sound.SoundRouter;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** See {@link SoundRouter}: de-duplicate across world copies and position sounds around the nearest player. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@Shadow
	@Final
	private Listener listener;

	@Shadow
	@Final
	private SoundEngineExecutor executor;

	@Shadow
	private boolean loaded;

	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void couchcraft$deduplicate(final SoundInstance instance, final CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (!SoundRouter.shouldPlay(instance)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@Inject(method = "updateSource", at = @At("HEAD"), cancellable = true)
	private void couchcraft$parkListener(final Camera camera, final CallbackInfo ci) {
		if (SoundRouter.active()) {
			if (this.loaded) {
				this.executor.execute(() -> this.listener.setTransform(SoundRouter.ORIGIN_LISTENER));
			}

			ci.cancel();
		}
	}

	@ModifyExpressionValue(method = "play", at = @At(value = "NEW", target = "(DDD)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 couchcraft$placeNewSound(final Vec3 world, @Local(argsOnly = true) final SoundInstance instance) {
		return SoundRouter.toListenerSpace(world, instance.isRelative());
	}

	@ModifyExpressionValue(method = "tickInGameSound", at = @At(value = "NEW", target = "(DDD)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 couchcraft$moveTickingSound(final Vec3 world, @Local final TickableSoundInstance instance) {
		return SoundRouter.toListenerSpace(world, instance.isRelative());
	}
}
