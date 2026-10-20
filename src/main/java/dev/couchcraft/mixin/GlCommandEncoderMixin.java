package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes a vanilla bug in the OpenGL backend: {@code copyTextureToTexture} passes the copy's width and height where
 * {@code glBlitFramebuffer} expects end coordinates. Copies at offset 0 (all vanilla does) are unaffected; any other
 * offset produced a mirrored sliver. Couchcraft composites viewports at offsets, so it needs the correct behaviour.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderMixin {
	@WrapOperation(
		method = "copyTextureToTexture",
		at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/DirectStateAccess;blitFrameBuffers(IIIIIIIIIIII)V")
	)
	private void couchcraft$blitWithEndCoordinates(
		final DirectStateAccess instance,
		final int source,
		final int dest,
		final int srcX0,
		final int srcY0,
		final int width,
		final int height,
		final int dstX0,
		final int dstY0,
		final int dstWidth,
		final int dstHeight,
		final int mask,
		final int filter,
		final Operation<Void> original
	) {
		original.call(instance, source, dest, srcX0, srcY0, srcX0 + width, srcY0 + height, dstX0, dstY0, dstX0 + dstWidth, dstY0 + dstHeight, mask, filter);
	}
}
