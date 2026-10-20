package dev.couchcraft.gui;

import dev.couchcraft.profile.Profile;
import dev.couchcraft.profile.Profiles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Type a profile name. Keyboard players type into the box; controller players point at the on-screen keys with
 * their cursor and press A. Minecraft player names: 3-16 letters, numbers or underscores.
 */
public final class NameEntryScreen extends Screen implements CursorHint {
	private static final String[] ROWS = {"1234567890", "qwertyuiop", "asdfghjkl_", "zxcvbnm"};
	private static final int KEY = 20;
	private static final int GAP = 2;

	private final Screen parent;
	private final String initial;
	private final @Nullable Profile editing;
	private final Consumer<String> onDone;
	private final @Nullable Runnable onDelete;
	private final List<Button> letterKeys = new ArrayList<>();
	private @Nullable EditBox box;
	private @Nullable StringWidget problem;
	private @Nullable Button done;
	private boolean upperCase;

	/**
	 * @param editing  the profile being renamed (so its own current name counts as free), or null for a new one
	 * @param onDelete shows a Delete button when not null
	 */
	public NameEntryScreen(
		final Screen parent,
		final Component title,
		final String initial,
		final @Nullable Profile editing,
		final Consumer<String> onDone,
		final @Nullable Runnable onDelete
	) {
		super(title);
		this.parent = parent;
		this.initial = initial;
		this.editing = editing;
		this.onDone = onDone;
		this.onDelete = onDelete;
	}

	@Override
	protected void init() {
		String keep = this.box != null ? this.box.getValue() : this.initial;
		this.letterKeys.clear();
		int keyboardWidth = 10 * KEY + 9 * GAP;
		int contentHeight = 12 + 22 + 12 + ROWS.length * (KEY + GAP) + 6 + 20;
		int left = (this.width - keyboardWidth) / 2;
		int y = Math.max(6, (this.height - contentHeight) / 2);

		StringWidget titleWidget = new StringWidget(this.title, this.font);
		titleWidget.setPosition((this.width - titleWidget.getWidth()) / 2, y);
		this.addRenderableWidget(titleWidget);
		y += 12;

		EditBox box = new EditBox(this.font, left, y, keyboardWidth, 20, Component.translatableWithFallback("couchcraft.name.label", "Name"));
		box.setMaxLength(Profiles.MAX_NAME_LENGTH);
		box.setValue(keep);
		box.setResponder(this::onTyped);
		this.box = this.addRenderableWidget(box);
		y += 22;

		this.problem = this.addRenderableWidget(new StringWidget(left, y, keyboardWidth, 10, Component.empty(), this.font));
		y += 12;

		for (String row : ROWS) {
			int x = left;
			for (char c : row.toCharArray()) {
				Button key = Button.builder(Component.literal(String.valueOf(c)), button -> this.type(button.getMessage().getString()))
					.bounds(x, y, KEY, KEY)
					.build();
				this.letterKeys.add(this.addRenderableWidget(key));
				x += KEY + GAP;
			}

			if (row.length() < 10) {
				int wide = (keyboardWidth - (x - left) - GAP) / 2;
				this.addRenderableWidget(Button.builder(Component.literal("aA"), button -> this.toggleCase()).bounds(x, y, wide, KEY).build());
				this.addRenderableWidget(
					Button.builder(Component.translatableWithFallback("couchcraft.name.backspace", "Del"), button -> this.backspace()).bounds(x + wide + GAP, y, keyboardWidth - (x - left) - wide - GAP, KEY).build()
				);
			}

			y += KEY + GAP;
		}

		y += 4;
		int buttons = this.onDelete != null ? 3 : 2;
		int buttonWidth = (keyboardWidth - (buttons - 1) * GAP) / buttons;
		int x = left;
		this.done = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.finish()).bounds(x, y, buttonWidth, 20).build());
		x += buttonWidth + GAP;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).bounds(x, y, buttonWidth, 20).build());
		if (this.onDelete != null) {
			x += buttonWidth + GAP;
			this.addRenderableWidget(
				Button.builder(Component.translatableWithFallback("couchcraft.name.delete", "Delete").withStyle(ChatFormatting.RED), button -> this.onDelete.run())
					.bounds(x, y, buttonWidth, 20)
					.build()
			);
		}

		this.applyCase();
		this.validate();
	}

	@Override
	protected void setInitialFocus() {
		if (this.box != null) {
			this.setInitialFocus(this.box);
		}
	}

	private void onTyped(final String value) {
		StringBuilder clean = new StringBuilder();
		value.chars().filter(Profiles::isNameChar).forEach(c -> clean.append((char)c));
		if (!clean.toString().equals(value) && this.box != null) {
			this.box.setValue(clean.toString());
			return;
		}

		this.validate();
	}

	private void type(final String text) {
		if (this.box != null) {
			this.box.insertText(text);
		}
	}

	private void backspace() {
		if (this.box != null) {
			this.box.deleteChars(-1);
		}
	}

	private void toggleCase() {
		this.upperCase = !this.upperCase;
		this.applyCase();
	}

	private void applyCase() {
		for (Button key : this.letterKeys) {
			String label = key.getMessage().getString();
			key.setMessage(Component.literal(this.upperCase ? label.toUpperCase(java.util.Locale.ROOT) : label.toLowerCase(java.util.Locale.ROOT)));
		}
	}

	private void validate() {
		if (this.box == null || this.done == null || this.problem == null) {
			return;
		}

		String issue = Profiles.problemWith(this.box.getValue(), this.editing);
		this.done.active = issue == null;
		this.problem.setMessage(issue == null ? Component.empty() : Component.literal(issue).withStyle(ChatFormatting.RED));
	}

	private void finish() {
		if (this.box != null && Profiles.problemWith(this.box.getValue(), this.editing) == null) {
			this.onDone.accept(this.box.getValue());
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public @Nullable AbstractWidget couchcraft$cursorTarget() {
		return this.done;
	}
}
