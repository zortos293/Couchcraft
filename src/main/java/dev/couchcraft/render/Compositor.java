package dev.couchcraft.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.context.Viewport;
import java.util.List;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

/**
 * Stitches every player's finished frame into one window-sized texture, which vanilla then presents as usual.
 * Pure GPU copies: no shaders, no extra draw calls.
 */
public final class Compositor {
	private static final Vector4fc DIVIDER = new Vector4f(0.067F, 0.067F, 0.067F, 1.0F);
	private static @Nullable TextureTarget target;

	private Compositor() {
	}

	public static void composite(final List<PlayerContext> contexts, final int width, final int height) {
		if (target == null) {
			target = new TextureTarget("Couchcraft composite", width, height, GpuFormat.RGBA8_UNORM, null);
		} else if (target.width != width || target.height != height) {
			target.resize(width, height);
		}

		GpuTexture output = target.getColorTexture();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.clearColorTexture(output, DIVIDER);

		for (PlayerContext context : contexts) {
			if (context.isRemoved()) {
				continue;
			}

			RenderTarget source = context.gameRenderer.mainRenderTarget();
			GpuTexture color = source.getColorTexture();
			if (color == null) {
				continue;
			}

			Viewport viewport = context.viewport();
			int copyWidth = Math.min(source.width, Math.min(viewport.width(), width - viewport.x()));
			int copyHeight = Math.min(source.height, Math.min(viewport.height(), height - viewport.y()));
			if (copyWidth <= 0 || copyHeight <= 0) {
				continue;
			}

			// Texture regions use a bottom-left origin on every backend (same convention vanilla uses for sub-rects).
			int destY = height - viewport.y() - copyHeight;
			int sourceY = source.height - copyHeight;
			encoder.copyTextureToTexture(color, output, 0, viewport.x(), destY, 0, sourceY, copyWidth, copyHeight);
		}
	}

	public static @Nullable RenderTarget target() {
		return target;
	}

	public static @Nullable GpuTextureView view() {
		return target == null ? null : target.getColorTextureView();
	}

	public static void release() {
		if (target != null) {
			target.destroyBuffers();
			target = null;
		}
	}
}
