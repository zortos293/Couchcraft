package dev.couchcraft.gui;

import dev.couchcraft.CouchConfig;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.Layouts;
import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.input.Gamepad;
import dev.couchcraft.input.Gamepads;
import dev.couchcraft.profile.Profile;
import dev.couchcraft.profile.Profiles;
import net.minecraft.client.gui.screens.ConfirmScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Who plays with what. One row per player (their keyboard/controller and a selector to change it), one row per
 * connected controller (live activity light, owner, and a Vibrate button to find it on the couch), then settings.
 */
public final class CouchScreen extends OptionsSubScreen {
	private static final int ACTIVE_COLOR = 0x55FF55;
	private static final int IDLE_COLOR = 0x555555;
	private final List<Runnable> refreshers = new ArrayList<>();
	private String signature = "";

	public CouchScreen(final @Nullable Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, text("couchcraft.screen.title", "Couch Co-op · Controllers"));
	}

	private static MutableComponent text(final String key, final String fallback, final Object... args) {
		return Component.translatableWithFallback(key, fallback, args);
	}

	/** A pad option for the per-player selector; a null pad means "no controller". */
	private record PadChoice(@Nullable Gamepad pad) {
	}

	@Override
	protected void addOptions() {
		this.signature = signature();
		this.refreshers.clear();
		this.addPlayers();
		this.addProfiles();
		this.addControllers();
		this.addSettings();
	}

	// ---------------------------------------------------------------- players

	private void addPlayers() {
		this.list.addHeader(text("couchcraft.screen.players", "Players"));
		if (!Couch.ready()) {
			return;
		}

		for (PlayerContext context : Couch.contexts()) {
			this.addPlayer(context);
		}

		if (Couch.canAddGuest(this.minecraft)) {
			this.list.addSmall(
				label(text("couchcraft.screen.add_hint", "or START on a free pad")),
				Button.builder(text("couchcraft.screen.add", "Add player"), button -> this.minecraft.gui.setScreen(new ProfileSelectScreen(this, null, null, profile -> {
					Couch.requestAddGuest(null, profile);
					this.minecraft.gui.setScreen(new CouchScreen(this.lastScreen));
				}, () -> this.minecraft.gui.setScreen(this)))).width(150).build()
			);
		} else if (Couch.contexts().size() < Couch.MAX_PLAYERS) {
			this.list.addSmall(label(text("couchcraft.screen.add_unavailable", "More players can join in a singleplayer world").withStyle(ChatFormatting.GRAY)), null);
		}
	}

	private void addPlayer(final PlayerContext context) {
		StringWidget name = label(playerLabel(context));
		AbstractWidget right = context.primary
			? label(text("couchcraft.screen.keyboard", "+ Keyboard & mouse").withStyle(ChatFormatting.GRAY))
			: Button.builder(text("couchcraft.screen.remove", "Remove %s", context.name), button -> {
				Couch.requestRemove(context);
				button.active = false;
			}).width(150).build();
		this.list.addSmall(name, right);

		List<PadChoice> choices = new ArrayList<>();
		choices.add(new PadChoice(null));
		for (Gamepad pad : Gamepads.connected()) {
			choices.add(new PadChoice(pad));
		}

		CycleButton<PadChoice> selector = CycleButton.<PadChoice>builder(choice -> choiceLabel(choice, context), new PadChoice(context.pad))
			.withValues(choices)
			.create(text("couchcraft.screen.controller", "Controller"), (button, choice) -> Couch.assignPad(context, choice.pad()));
		Button vibrate = Button.builder(text("couchcraft.screen.vibrate", "Vibrate"), button -> {
			if (context.pad != null) {
				context.pad.identify();
			}
		}).width(150).build();
		this.list.addSmall(selector, vibrate);

		this.refreshers.add(() -> {
			name.setMessage(playerLabel(context));
			selector.setValue(new PadChoice(context.pad));
			vibrate.active = context.pad != null && context.pad.isConnected();
		});
	}

	private static Component playerLabel(final PlayerContext context) {
		MutableComponent label = Component.literal("P" + (context.slot + 1)).withColor(context.color() & 0xFFFFFF).withStyle(ChatFormatting.BOLD);
		if (!context.primary && context.profile == null) {
			return label.append(text("couchcraft.screen.choosing", "  choosing a profile…").withStyle(ChatFormatting.GRAY));
		}

		return label.append(Component.literal("  " + context.name).withStyle(ChatFormatting.WHITE));
	}

	private static Component choiceLabel(final PadChoice choice, final PlayerContext context) {
		if (choice.pad() == null) {
			return context.primary ? text("couchcraft.screen.none_p1", "Keyboard only") : text("couchcraft.screen.none", "None");
		}

		MutableComponent label = Component.literal(Gamepads.number(choice.pad()) + " · " + choice.pad().name());
		PlayerContext owner = choice.pad().owner();
		if (owner != null && owner != context) {
			label.append(Component.literal(" (P" + (owner.slot + 1) + ")").withStyle(ChatFormatting.GRAY));
		}

		return label;
	}

	// ---------------------------------------------------------------- profiles

	private void addProfiles() {
		this.list.addHeader(text("couchcraft.screen.profiles", "Profiles · each keeps its own save in every world"));
		for (Profile profile : Profiles.all()) {
			PlayerContext user = Profiles.playerUsing(profile);
			MutableComponent label = Component.literal(profile.name()).withStyle(ChatFormatting.WHITE);
			if (user != null) {
				label.append(Component.literal("  playing as P" + (user.slot + 1)).withColor(user.color() & 0xFFFFFF));
			}

			Button rename = Button.builder(text("couchcraft.profiles.rename", "Rename"), button -> this.minecraft.gui.setScreen(new NameEntryScreen(
				this,
				text("couchcraft.profiles.rename_title", "Rename %s", profile.name()),
				profile.name(),
				profile,
				name -> {
					Profiles.rename(profile, name);
					this.minecraft.gui.setScreen(new CouchScreen(this.lastScreen));
				},
				() -> this.minecraft.gui.setScreen(new ConfirmScreen(
					confirmed -> {
						if (confirmed) {
							Profiles.delete(profile);
						}

						this.minecraft.gui.setScreen(new CouchScreen(this.lastScreen));
					},
					text("couchcraft.profiles.delete_title", "Delete %s?", profile.name()),
					text("couchcraft.profiles.delete_body", "The profile is removed from this list. Whatever it saved in your worlds stays in those worlds.")
				))
			))).width(150).build();
			// A profile that's playing keeps its name until that player leaves.
			rename.active = user == null;
			this.list.addSmall(label(label), rename);
		}

		this.list.addSmall(
			label(text("couchcraft.screen.profiles_hint", "Joining players pick one").withStyle(ChatFormatting.GRAY)),
			Button.builder(text("couchcraft.profiles.new", "New profile"), button -> this.minecraft.gui.setScreen(new NameEntryScreen(
				this,
				text("couchcraft.profiles.new_title", "New profile"),
				Profiles.suggestName(),
				null,
				name -> {
					Profiles.create(name);
					this.minecraft.gui.setScreen(new CouchScreen(this.lastScreen));
				},
				null
			))).width(150).build()
		);
	}

	// ---------------------------------------------------------------- controllers

	private void addControllers() {
		this.list.addHeader(text("couchcraft.screen.controllers", "Controllers · press a button to light up its row"));
		List<Gamepad> pads = Gamepads.connected();
		if (pads.isEmpty()) {
			this.list.addSmall(label(text("couchcraft.screen.no_controllers", "No controllers connected").withStyle(ChatFormatting.GRAY)), null);
			return;
		}

		for (Gamepad pad : pads) {
			StringWidget row = label(controllerLabel(pad));
			Button vibrate = Button.builder(text("couchcraft.screen.vibrate", "Vibrate"), button -> pad.identify()).width(150).build();
			this.list.addSmall(row, vibrate);
			this.refreshers.add(() -> row.setMessage(controllerLabel(pad)));
		}
	}

	private static Component controllerLabel(final Gamepad pad) {
		boolean lit = pad.isActive() || pad.isIdentifying();
		MutableComponent label = Component.literal("● ").withColor(lit ? ACTIVE_COLOR : IDLE_COLOR);
		label.append(Component.literal(Gamepads.number(pad) + " · " + pad.name() + "  ").withStyle(ChatFormatting.WHITE));
		PlayerContext owner = pad.owner();
		if (owner == null) {
			label.append(text("couchcraft.screen.free", "free").withStyle(ChatFormatting.GRAY));
		} else {
			label.append(Component.literal("P" + (owner.slot + 1)).withColor(owner.color() & 0xFFFFFF).withStyle(ChatFormatting.BOLD));
		}

		return label;
	}

	// ---------------------------------------------------------------- settings

	private void addSettings() {
		CouchConfig config = CouchConfig.get();
		this.list.addHeader(text("couchcraft.screen.settings", "Controller settings"));
		this.list.addSmall(
			new CouchSlider(
				text("couchcraft.screen.look_speed", "Look speed"),
				60.0,
				600.0,
				() -> config.lookSpeedYaw,
				value -> {
					// Keep vertical look proportional to horizontal, as in the defaults (280 / 190).
					config.lookSpeedYaw = (float)value;
					config.lookSpeedPitch = (float)(value * 190.0 / 280.0);
				},
				value -> Math.round(value) + "°/s"
			),
			CycleButton.onOffBuilder(config.invertLookY)
				.create(text("couchcraft.screen.invert", "Invert look"), (button, value) -> config.invertLookY = value)
		);
		this.list.addSmall(
			new CouchSlider(
				text("couchcraft.screen.deadzone", "Stick deadzone"), 0.05, 0.40, () -> config.stickDeadzone, value -> config.stickDeadzone = (float)value,
				value -> Math.round(value * 100.0) + "%"
			),
			new CouchSlider(
				text("couchcraft.screen.cursor_speed", "Menu cursor speed"), 100.0, 800.0, () -> config.cursorSpeed, value -> config.cursorSpeed = (float)value,
				value -> String.valueOf(Math.round(value))
			)
		);
		this.list.addSmall(
			CycleButton.onOffBuilder(config.rumble).create(text("couchcraft.screen.rumble", "Rumble on damage"), (button, value) -> config.rumble = value),
			CycleButton.onOffBuilder(config.showJoinHint).create(text("couchcraft.screen.join_hint", "Join hint"), (button, value) -> config.showJoinHint = value)
		);
		this.list.addSmall(
			CycleButton.<Layouts.TwoPlayer>builder(
					layout -> layout == Layouts.TwoPlayer.STACKED
						? text("couchcraft.screen.stacked", "Stacked")
						: text("couchcraft.screen.side_by_side", "Side by side"),
					config.twoPlayerLayout()
				)
				.withValues(Layouts.TwoPlayer.values())
				.create(text("couchcraft.screen.two_players", "2 players"), (button, value) -> {
					config.twoPlayerLayout = value.name();
					Couch.markLayoutDirty();
				}),
			null
		);
	}

	// ---------------------------------------------------------------- lifecycle

	private StringWidget label(final Component message) {
		return new StringWidget(150, 20, message, this.font).setMaxWidth(150, StringWidget.TextOverflow.SCROLLING);
	}

	/** Changes that alter which rows exist (a controller plugged in, a player joined) rebuild the screen. */
	private static String signature() {
		StringBuilder builder = new StringBuilder();
		if (Couch.ready()) {
			for (PlayerContext context : Couch.contexts()) {
				builder.append(context.slot).append(context.name).append(';');
			}
		}

		builder.append('|');
		for (Profile profile : Profiles.all()) {
			builder.append(profile.name()).append(';');
		}

		builder.append('|');
		for (Gamepad pad : Gamepads.connected()) {
			builder.append(System.identityHashCode(pad)).append(';');
		}

		return builder.toString();
	}

	@Override
	public void tick() {
		super.tick();
		if (!signature().equals(this.signature)) {
			this.minecraft.gui.setScreen(new CouchScreen(this.lastScreen));
			return;
		}

		this.refreshers.forEach(Runnable::run);
	}

	@Override
	public void removed() {
		super.removed();
		CouchConfig.save();
	}
}
