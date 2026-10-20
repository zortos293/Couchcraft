package dev.couchcraft;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

public final class Couchcraft implements ClientModInitializer {
	public static final String MOD_ID = "couchcraft";
	private static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitializeClient() {
		CouchConfig.load();
		LOGGER.info("[Couchcraft] Loaded. Single-client split screen for up to 4 players.");
	}
}
