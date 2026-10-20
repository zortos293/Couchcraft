package dev.couchcraft.context;

/**
 * A player's rectangle on the window, in framebuffer pixels with a top-left origin.
 */
public record Viewport(int x, int y, int width, int height) {
	public boolean contains(double px, double py) {
		return px >= this.x && py >= this.y && px < this.x + this.width && py < this.y + this.height;
	}
}
