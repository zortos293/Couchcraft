package dev.couchcraft.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import org.jspecify.annotations.Nullable;

/**
 * A screen that knows where a controller player most likely wants to click first. The controller cursor starts on
 * that widget instead of the middle of the screen, so the default choice is one press of A away.
 */
public interface CursorHint {
	@Nullable AbstractWidget couchcraft$cursorTarget();
}
