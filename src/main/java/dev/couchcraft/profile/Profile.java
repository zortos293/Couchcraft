package dev.couchcraft.profile;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A couch guest's identity. The {@link #id} is fixed for the life of the profile, and it is what the world saves the
 * player's inventory, XP, advancements and spawn under, so renaming a profile keeps all of that.
 */
public final class Profile {
	final UUID id;
	String name;
	/** The controller this profile last played with, used to pre-select it when that controller joins again. */
	@Nullable String lastDevice;
	long lastPlayed;

	Profile(final UUID id, final String name) {
		this.id = id;
		this.name = name;
	}

	public UUID id() {
		return this.id;
	}

	public String name() {
		return this.name;
	}

	public @Nullable String lastDevice() {
		return this.lastDevice;
	}

	public long lastPlayed() {
		return this.lastPlayed;
	}
}
