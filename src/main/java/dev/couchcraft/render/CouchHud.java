package dev.couchcraft.render;

import dev.couchcraft.CouchConfig;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.input.Gamepads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * Couchcraft's own overlay, drawn last into each player's GUI: the name badge, the join hint and,
 * for guests in a menu, their controller cursor.
 */
public final class CouchHud {
	private static final long BADGE_MILLIS = 4000L;
	private static final int INK = 0xFF111111;

	/** 11x16 arrow. '#' is outline, '+' is fill. */
	private static final String[] CURSOR = {
		"#          ",
		"##         ",
		"#+#        ",
		"#++#       ",
		"#+++#      ",
		"#++++#     ",
		"#+++++#    ",
		"#++++++#   ",
		"#+++++++#  ",
		"#++++++++# ",
		"#+++++#####",
		"#++#++#    ",
		"#+# #++#   ",
		"##  #++#   ",
		"     #++#  ",
		"     ###   "
	};

	private CouchHud() {
	}

	public static void extract(final GuiGraphicsExtractor graphics) {
		if (!Couch.ready()) {
			return;
		}

		PlayerContext context = Couch.current();
		if (context == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		graphics.nextStratum();
		if (Couch.isSplit()) {
			// A permanent hairline in the player's color along the top edge of their view.
			graphics.fill(0, 0, graphics.guiWidth(), 1, context.color());
		}

		drawBadge(graphics, minecraft, context);
		if (context.primary && minecraft.level != null && CouchConfig.get().showJoinHint && Gamepads.anyUnclaimed()) {
			if (Couch.canAddGuest(minecraft)) {
				drawHint(graphics, minecraft, Component.translatableWithFallback("couchcraft.hint.join", "Controller: START to join · BACK to play as P1"));
			} else if (context.pad == null) {
				drawHint(graphics, minecraft, Component.translatableWithFallback("couchcraft.hint.p1", "Controller: BACK to play as P1"));
			}
		}

		if (!context.primary && minecraft.gui.screen() != null && context.controller.hasCursor()) {
			int x = (int)context.controller.cursorGuiX(minecraft.getWindow());
			int y = (int)context.controller.cursorGuiY(minecraft.getWindow());
			drawCursor(graphics, x, y, context.color());
		}
	}

	/** A name badge that fades out a few seconds after joining or picking up a controller. */
	private static void drawBadge(final GuiGraphicsExtractor graphics, final Minecraft minecraft, final PlayerContext context) {
		if (!context.primary && context.profile == null) {
			// Still picking a profile: the name isn't theirs yet.
			return;
		}

		long age = System.currentTimeMillis() - context.badgeSince;
		if (age < 0L || age > BADGE_MILLIS || minecraft.gui.screen() != null && minecraft.level != null) {
			return;
		}

		float fade = age < BADGE_MILLIS - 600L ? 1.0F : (BADGE_MILLIS - age) / 600.0F;
		int alpha = Math.round(fade * 255.0F);
		String label = "P" + (context.slot + 1) + "  " + context.name + (context.pad != null ? "  ·  " + context.pad.name() : "");
		int textWidth = minecraft.font.width(label);
		graphics.fill(4, 5, 4 + textWidth + 10, 5 + 15, ARGB.color(alpha, context.color()));
		graphics.text(minecraft.font, label, 9, 9, ARGB.color(alpha, context.slot == 0 ? INK : 0xFFFFFFFF), false);
	}

	private static void drawHint(final GuiGraphicsExtractor graphics, final Minecraft minecraft, final Component hint) {
		int width = minecraft.font.width(hint);
		int x = graphics.guiWidth() - width - 6;
		int y = graphics.guiHeight() - 14;
		float pulse = 0.65F + 0.35F * (float)Math.sin(System.currentTimeMillis() / 400.0);
		graphics.text(minecraft.font, hint, x, y, ARGB.color(Math.round(pulse * 255.0F), 0xFFFFFF), true);
	}

	private static void drawCursor(final GuiGraphicsExtractor graphics, final int x, final int y, final int color) {
		for (int row = 0; row < CURSOR.length; row++) {
			String line = CURSOR[row];
			for (int col = 0; col < line.length(); col++) {
				char c = line.charAt(col);
				if (c == '#') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, INK);
				} else if (c == '+') {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
				}
			}
		}
	}
}
