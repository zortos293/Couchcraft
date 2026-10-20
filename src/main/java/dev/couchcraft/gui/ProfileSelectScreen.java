package dev.couchcraft.gui;

import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.profile.Profile;
import dev.couchcraft.profile.Profiles;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * "Who's playing?" A joining player picks one of the saved profiles (the one last used with their controller is
 * offered first), makes a new one, or renames one. Each profile keeps its own save in every world.
 */
public final class ProfileSelectScreen extends OptionsSubScreen implements CursorHint {
	private final @Nullable PlayerContext joining;
	private final @Nullable String deviceKey;
	private final Consumer<Profile> onPlay;
	private final Runnable onCancel;
	private @Nullable AbstractWidget cursorTarget;
	private boolean done;

	/**
	 * @param joining   the player who is choosing, shown in the title; null when someone picks for a new player
	 * @param deviceKey the controller choosing, to offer the profile it played with last time
	 */
	public ProfileSelectScreen(
		final @Nullable Screen parent,
		final @Nullable PlayerContext joining,
		final @Nullable String deviceKey,
		final Consumer<Profile> onPlay,
		final Runnable onCancel
	) {
		super(parent, Minecraft.getInstance().options, title(joining));
		this.joining = joining;
		this.deviceKey = deviceKey;
		this.onPlay = onPlay;
		this.onCancel = onCancel;
	}

	private static Component title(final @Nullable PlayerContext joining) {
		if (joining == null) {
			return Component.translatableWithFallback("couchcraft.profiles.title_add", "Add a player · who's playing?");
		}

		return Component.translatableWithFallback("couchcraft.profiles.title", "Who's playing?  ")
			.append(Component.literal("P" + (joining.slot + 1)).withColor(joining.color() & 0xFFFFFF).withStyle(ChatFormatting.BOLD));
	}

	private Screen copy() {
		return new ProfileSelectScreen(this.lastScreen, this.joining, this.deviceKey, this.onPlay, this.onCancel);
	}

	@Override
	protected void addOptions() {
		Profile suggested = Profiles.suggestFor(this.deviceKey);
		if (suggested != null) {
			MutableComponent play = Component.translatableWithFallback("couchcraft.profiles.play", "Play as %s", suggested.name());
			if (this.deviceKey != null && this.deviceKey.equals(suggested.lastDevice())) {
				play = play.append(Component.literal("  ★").withStyle(ChatFormatting.GOLD));
			}

			Button playButton = Button.builder(play, button -> this.choose(suggested)).width(310).build();
			this.list.addBig(playButton);
			this.cursorTarget = playButton;
		}

		this.list.addHeader(Component.translatableWithFallback("couchcraft.profiles.saved", "Saved profiles"));
		for (Profile profile : Profiles.all()) {
			PlayerContext user = Profiles.playerUsing(profile);
			MutableComponent label = Component.literal(profile.name());
			if (user != null) {
				label.append(Component.literal("  · P" + (user.slot + 1)).withColor(user.color() & 0xFFFFFF));
			}

			Button pick = Button.builder(label, button -> this.choose(profile)).width(150).build();
			pick.active = user == null;
			Button rename = Button.builder(Component.translatableWithFallback("couchcraft.profiles.rename", "Rename"), button -> this.rename(profile))
				.width(150)
				.build();
			rename.active = user == null;
			this.list.addSmall(pick, rename);
		}

		Button create = Button.builder(Component.translatableWithFallback("couchcraft.profiles.new", "New profile"), button -> this.createNew())
			.width(150)
			.build();
		if (this.cursorTarget == null) {
			this.cursorTarget = create;
		}

		this.list.addSmall(create, null);
	}

	@Override
	protected void addFooter() {
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(200).build());
	}

	private void choose(final Profile profile) {
		if (!this.done && !Profiles.inUse(profile)) {
			this.done = true;
			this.onPlay.accept(profile);
		}
	}

	private void createNew() {
		this.minecraft.gui.setScreen(new NameEntryScreen(
			this,
			Component.translatableWithFallback("couchcraft.profiles.new_title", "New profile"),
			Profiles.suggestName(),
			null,
			name -> this.choose(Profiles.create(name)),
			null
		));
	}

	private void rename(final Profile profile) {
		this.minecraft.gui.setScreen(new NameEntryScreen(
			this,
			Component.translatableWithFallback("couchcraft.profiles.rename_title", "Rename %s", profile.name()),
			profile.name(),
			profile,
			name -> {
				Profiles.rename(profile, name);
				this.minecraft.gui.setScreen(this.copy());
			},
			() -> this.minecraft.gui.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						Profiles.delete(profile);
					}

					this.minecraft.gui.setScreen(this.copy());
				},
				Component.translatableWithFallback("couchcraft.profiles.delete_title", "Delete %s?", profile.name()),
				Component.translatableWithFallback(
					"couchcraft.profiles.delete_body",
					"The profile is removed from this list. Whatever it saved in your worlds stays in those worlds."
				)
			))
		));
	}

	@Override
	public void onClose() {
		if (!this.done) {
			this.done = true;
			this.onCancel.run();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public @Nullable AbstractWidget couchcraft$cursorTarget() {
		return this.cursorTarget;
	}
}
