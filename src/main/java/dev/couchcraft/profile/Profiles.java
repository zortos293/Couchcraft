package dev.couchcraft.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.couchcraft.CouchConfig;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Guest profiles, stored in {@code config/couchcraft-profiles.json}. The first time it runs it adopts the old
 * {@code guestNames} (Player2..4) with the IDs those players already had, so no existing saves are lost.
 */
public final class Profiles {
	public static final int MAX_NAME_LENGTH = 16;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Profile> PROFILES = new ArrayList<>();
	private static boolean loaded;

	private Profiles() {
	}

	/** All profiles, most recently played first. */
	public static List<Profile> all() {
		ensureLoaded();
		List<Profile> sorted = new ArrayList<>(PROFILES);
		sorted.sort(Comparator.comparingLong(Profile::lastPlayed).reversed());
		return sorted;
	}

	/** The player currently using {@code profile}, if any. */
	public static @Nullable PlayerContext playerUsing(final Profile profile) {
		if (!Couch.ready()) {
			return null;
		}

		for (PlayerContext context : Couch.contexts()) {
			if (context.profile == profile && !context.isRemoved()) {
				return context;
			}
		}

		return null;
	}

	public static boolean inUse(final Profile profile) {
		return playerUsing(profile) != null;
	}

	/** The free profile this controller played with most recently, or the most recent free profile. */
	public static @Nullable Profile suggestFor(final @Nullable String deviceKey) {
		Profile fallback = null;
		for (Profile profile : all()) {
			if (inUse(profile)) {
				continue;
			}

			if (deviceKey != null && deviceKey.equals(profile.lastDevice)) {
				return profile;
			}

			if (fallback == null) {
				fallback = profile;
			}
		}

		return fallback;
	}

	public static @Nullable Profile firstFree() {
		return suggestFor(null);
	}

	/** Why {@code name} can't be used for {@code profile} (null to create), or null if it's fine. */
	public static @Nullable String problemWith(final String name, final @Nullable Profile profile) {
		if (name.length() < 3) {
			return "At least 3 characters";
		}

		if (name.length() > MAX_NAME_LENGTH || !name.chars().allMatch(Profiles::isNameChar)) {
			return "Letters, numbers and _ only";
		}

		if (name.equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) {
			return "That's the host's name";
		}

		for (Profile other : all()) {
			if (other != profile && other.name.equalsIgnoreCase(name)) {
				return "Another profile has that name";
			}
		}

		return null;
	}

	public static boolean isNameChar(final int c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
	}

	public static Profile create(final String name) {
		ensureLoaded();
		// A new profile starts with the ID a player of that name always had, so a world that already knows
		// "Steve" from earlier still has Steve's things; renaming later keeps this ID.
		UUID id = UUIDUtil.createOfflinePlayerUUID(name);
		for (Profile other : PROFILES) {
			if (other.id.equals(id)) {
				id = UUID.randomUUID();
				break;
			}
		}

		Profile profile = new Profile(id, name);
		profile.lastPlayed = System.currentTimeMillis();
		PROFILES.add(profile);
		save();
		LOGGER.info("[Couchcraft] Created profile {}", name);
		return profile;
	}

	public static void rename(final Profile profile, final String name) {
		LOGGER.info("[Couchcraft] Renamed profile {} to {} (saves stay with it)", profile.name, name);
		profile.name = name;
		save();
	}

	public static void delete(final Profile profile) {
		PROFILES.remove(profile);
		save();
		LOGGER.info("[Couchcraft] Deleted profile {}", profile.name);
	}

	/** Remembers that {@code profile} just started playing, with {@code deviceKey} if it's on a controller. */
	public static void markPlayed(final Profile profile, final @Nullable String deviceKey) {
		profile.lastPlayed = System.currentTimeMillis();
		if (deviceKey != null) {
			for (Profile other : PROFILES) {
				if (other != profile && deviceKey.equals(other.lastDevice)) {
					other.lastDevice = null;
				}
			}

			profile.lastDevice = deviceKey;
		}

		save();
	}

	/** A free default name like "Player5". */
	public static String suggestName() {
		for (int n = 2; ; n++) {
			String name = "Player" + n;
			if (problemWith(name, null) == null) {
				return name;
			}
		}
	}

	// ---------------------------------------------------------------- storage

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("couchcraft-profiles.json");
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}

		loaded = true;
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				JsonElement root = JsonParser.parseReader(reader);
				for (JsonElement element : root.getAsJsonObject().getAsJsonArray("profiles")) {
					JsonObject json = element.getAsJsonObject();
					Profile profile = new Profile(UUID.fromString(json.get("id").getAsString()), json.get("name").getAsString());
					profile.lastDevice = json.has("lastDevice") ? json.get("lastDevice").getAsString() : null;
					profile.lastPlayed = json.has("lastPlayed") ? json.get("lastPlayed").getAsLong() : 0L;
					PROFILES.add(profile);
				}

				return;
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("[Couchcraft] Could not read {}; keeping a backup and starting fresh", path, e);
				try {
					Files.copy(path, path.resolveSibling("couchcraft-profiles.broken.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
				}
			}
		}

		// First run: adopt the old fixed guest names with the IDs their saves already use.
		long order = 3L;
		for (String name : CouchConfig.get().guestNames) {
			if (name != null && name.length() >= 3 && name.length() <= MAX_NAME_LENGTH && name.chars().allMatch(Profiles::isNameChar)) {
				Profile profile = new Profile(UUIDUtil.createOfflinePlayerUUID(name), name);
				profile.lastPlayed = order--;
				PROFILES.add(profile);
			}
		}

		save();
	}

	private static void save() {
		JsonArray array = new JsonArray();
		for (Profile profile : PROFILES) {
			JsonObject json = new JsonObject();
			json.addProperty("id", profile.id.toString());
			json.addProperty("name", profile.name);
			if (profile.lastDevice != null) {
				json.addProperty("lastDevice", profile.lastDevice);
			}

			json.addProperty("lastPlayed", profile.lastPlayed);
			array.add(json);
		}

		JsonObject root = new JsonObject();
		root.add("profiles", array);
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Path temp = path.resolveSibling("couchcraft-profiles.json.tmp");
			try (Writer writer = Files.newBufferedWriter(temp)) {
				GSON.toJson(root, writer);
			}

			Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOGGER.warn("[Couchcraft] Could not save {}", path, e);
		}
	}

	static String normalize(final String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
