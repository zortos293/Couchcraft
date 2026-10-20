package dev.couchcraft.input;

/**
 * Stick shaping shared by look, movement and the menu cursor.
 */
public final class PadMath {
	private PadMath() {
	}

	/** Axial deadzone, rescaled so output starts at 0 just past the deadzone, then a quadratic response for precision. */
	public static float curve(final float value, final float deadzone) {
		float magnitude = Math.abs(value);
		if (magnitude <= deadzone) {
			return 0.0F;
		}

		float normalized = Math.min(1.0F, (magnitude - deadzone) / (1.0F - deadzone));
		return Math.copySign(normalized * normalized, value);
	}

	/** Radial deadzone for a whole stick, keeping direction exact. Returns {x, y} scaled to at most 1. */
	public static float[] radial(final float x, final float y, final float deadzone) {
		float magnitude = (float)Math.sqrt(x * x + y * y);
		if (magnitude <= deadzone) {
			return new float[]{0.0F, 0.0F};
		}

		float scaled = Math.min(1.0F, (magnitude - deadzone) / (1.0F - deadzone));
		return new float[]{x / magnitude * scaled, y / magnitude * scaled};
	}
}
