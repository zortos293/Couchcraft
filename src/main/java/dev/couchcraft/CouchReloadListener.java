package dev.couchcraft;

import dev.couchcraft.context.Couch;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Guests' renderers are not registered with the resource manager; this forwards reloads (F3+T, pack changes) to them.
 */
public final class CouchReloadListener implements ResourceManagerReloadListener {
	@Override
	public void onResourceManagerReload(final ResourceManager resourceManager) {
		Couch.onResourcesReloaded(Minecraft.getInstance());
	}
}
