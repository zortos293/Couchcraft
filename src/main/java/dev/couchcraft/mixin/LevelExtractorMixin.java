package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.couchcraft.context.Couch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Rebuild all chunks" requests from outside (graphics settings, F3+A, resource reloads) apply to every player's world.
 * The extractor's own per-player rebuilds (a new level, a render distance change) stay with that player.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Unique
	private static boolean couchcraft$local;

	@Unique
	private static boolean couchcraft$broadcasting;

	@WrapOperation(
		method = {"setLevel", "extract"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;allChanged()V")
	)
	private void couchcraft$perPlayer(final LevelExtractor instance, final Operation<Void> original) {
		boolean previous = couchcraft$local;
		couchcraft$local = true;
		try {
			original.call(instance);
		} finally {
			couchcraft$local = previous;
		}
	}

	@Inject(method = "allChanged", at = @At("HEAD"), cancellable = true)
	private void couchcraft$everyone(final CallbackInfo ci) {
		if (couchcraft$local || couchcraft$broadcasting || !Couch.isSplit()) {
			return;
		}

		couchcraft$broadcasting = true;
		try {
			Couch.forEachPlayer(() -> Minecraft.getInstance().levelExtractor.allChanged());
		} finally {
			couchcraft$broadcasting = false;
		}

		ci.cancel();
	}
}
