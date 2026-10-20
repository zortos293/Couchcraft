package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.couchcraft.context.Couch;
import dev.couchcraft.render.Compositor;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** F2 while split captures the whole couch, not just P1's view. */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
	@ModifyExpressionValue(
		method = "grab(Lnet/minecraft/client/Minecraft;Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;")
	)
	private static RenderTarget couchcraft$wholeCouch(final RenderTarget original) {
		RenderTarget composite = Couch.isSplit() ? Compositor.target() : null;
		return composite != null ? composite : original;
	}
}
