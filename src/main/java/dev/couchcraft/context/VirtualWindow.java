package dev.couchcraft.context;

import org.jspecify.annotations.Nullable;

/**
 * While a frame is active, {@code Window}'s size getters report the frame instead of the real window,
 * so vanilla code (camera aspect, GUI scale, screen layout, render target sizes) sees only one player's viewport.
 * The real window fields are never modified.
 */
public final class VirtualWindow {
	private static @Nullable Frame active;

	private VirtualWindow() {
	}

	public static @Nullable Frame active() {
		return active;
	}

	static void set(final @Nullable Frame frame) {
		active = frame;
	}

	/**
	 * @param framebufferWidth  viewport width in framebuffer pixels
	 * @param framebufferHeight viewport height in framebuffer pixels
	 * @param screenX           viewport origin in window (screen) coordinates, used to remap the real mouse
	 * @param screenY           viewport origin in window (screen) coordinates
	 * @param screenWidth       viewport width in window (screen) coordinates
	 * @param screenHeight      viewport height in window (screen) coordinates
	 */
	public record Frame(
		int framebufferWidth,
		int framebufferHeight,
		double screenX,
		double screenY,
		int screenWidth,
		int screenHeight,
		int guiScale,
		int guiScaledWidth,
		int guiScaledHeight
	) {
		public static Frame of(final Viewport viewport, final double screenPerPixelX, final double screenPerPixelY, final int guiScale) {
			int width = Math.max(1, viewport.width());
			int height = Math.max(1, viewport.height());
			return new Frame(
				width,
				height,
				viewport.x() * screenPerPixelX,
				viewport.y() * screenPerPixelY,
				Math.max(1, (int)Math.round(width * screenPerPixelX)),
				Math.max(1, (int)Math.round(height * screenPerPixelY)),
				guiScale,
				(int)Math.ceil((double)width / guiScale),
				(int)Math.ceil((double)height / guiScale)
			);
		}

		/**
		 * {@code Window.calculateScale} applied to a viewport, with a minimum GUI height of 224 instead of 240: the tallest
		 * vanilla menu (a six-row chest, 222) still fits, and a half-height viewport keeps a readable scale instead of 1.
		 */
		public static int calculateScale(final int width, final int height, final int maxScale, final boolean enforceUnicode) {
			int scale = 1;

			while (scale != maxScale && scale < width && scale < height && width / (scale + 1) >= 320 && height / (scale + 1) >= 224) {
				scale++;
			}

			if (enforceUnicode && scale % 2 != 0) {
				scale++;
			}

			return scale;
		}
	}
}
