package dev.couchcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import dev.couchcraft.context.Layouts;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.StringUtil;
import org.slf4j.Logger;

/**
 * {@code config/couchcraft.json}. Every field has a sensible default, so a missing or partial file is fine.
 */
public final class CouchConfig {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static CouchConfig instance = new CouchConfig();

	/** "STACKED" (P1 on top, the console default) or "SIDE_BY_SIDE". */
	public String twoPlayerLayout = Layouts.TwoPlayer.STACKED.name();
	/** Names for players 2-4. Each name maps to a stable offline UUID, so guests keep their inventory and spawn. */
	public String[] guestNames = {"Player2", "Player3", "Player4"};
	/** When the world has cheats enabled, guests may use commands too. */
	public boolean guestsInheritCheats = true;
	/** Full-tilt right stick turn speed, degrees per second. */
	public float lookSpeedYaw = 280.0F;
	public float lookSpeedPitch = 190.0F;
	public boolean invertLookY = false;
	public float stickDeadzone = 0.16F;
	/** Full-tilt menu cursor speed, GUI pixels per second. */
	public float cursorSpeed = 300.0F;
	public boolean rumble = true;
	public boolean showJoinHint = true;

	public static CouchConfig get() {
		return instance;
	}

	public Layouts.TwoPlayer twoPlayerLayout() {
		try {
			return Layouts.TwoPlayer.valueOf(this.twoPlayerLayout);
		} catch (IllegalArgumentException e) {
			return Layouts.TwoPlayer.STACKED;
		}
	}

	/** A valid, unique player name for a guest slot (1-3). */
	public String guestName(final int slot, final String hostName) {
		String name = slot - 1 < this.guestNames.length ? this.guestNames[slot - 1] : null;
		if (name == null || !StringUtil.isValidPlayerName(name) || name.isBlank()) {
			name = "Player" + (slot + 1);
		}

		if (name.equalsIgnoreCase(hostName)) {
			name = "Guest" + (slot + 1);
		}

		return name;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("couchcraft.json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				CouchConfig loaded = GSON.fromJson(reader, CouchConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("[Couchcraft] Could not read {}, using defaults", path, e);
			}
		}

		save();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("[Couchcraft] Could not write {}", path, e);
		}
	}
}
