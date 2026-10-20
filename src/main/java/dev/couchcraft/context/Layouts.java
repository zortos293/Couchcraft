package dev.couchcraft.context;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the window into one viewport per player, in slot order.
 */
public final class Layouts {
	private Layouts() {
	}

	public enum TwoPlayer {
		/** P1 on top, P2 below: the classic console default. */
		STACKED,
		/** P1 left, P2 right. */
		SIDE_BY_SIDE
	}

	public static int dividerThickness(final int windowHeight) {
		return Math.max(2, Math.round(windowHeight / 540.0F) * 2);
	}

	public static List<Viewport> compute(final int players, final int width, final int height, final TwoPlayer twoPlayer) {
		List<Viewport> result = new ArrayList<>(players);
		int d = dividerThickness(height);
		int leftW = (width - d) / 2;
		int rightX = leftW + d;
		int rightW = width - rightX;
		int topH = (height - d) / 2;
		int bottomY = topH + d;
		int bottomH = height - bottomY;

		switch (players) {
			case 1 -> result.add(new Viewport(0, 0, width, height));
			case 2 -> {
				if (twoPlayer == TwoPlayer.STACKED) {
					result.add(new Viewport(0, 0, width, topH));
					result.add(new Viewport(0, bottomY, width, bottomH));
				} else {
					result.add(new Viewport(0, 0, leftW, height));
					result.add(new Viewport(rightX, 0, rightW, height));
				}
			}
			case 3 -> {
				result.add(new Viewport(0, 0, width, topH));
				result.add(new Viewport(0, bottomY, leftW, bottomH));
				result.add(new Viewport(rightX, bottomY, rightW, bottomH));
			}
			default -> {
				result.add(new Viewport(0, 0, leftW, topH));
				result.add(new Viewport(rightX, 0, rightW, topH));
				result.add(new Viewport(0, bottomY, leftW, bottomH));
				result.add(new Viewport(rightX, bottomY, rightW, bottomH));
			}
		}

		return result;
	}
}
