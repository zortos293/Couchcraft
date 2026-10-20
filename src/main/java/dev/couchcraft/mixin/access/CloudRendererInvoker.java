package dev.couchcraft.mixin.access;

import java.util.Optional;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CloudRenderer.class)
public interface CloudRendererInvoker {
	@Invoker("prepare")
	Optional<CloudRenderer.TextureData> couchcraft$prepare(ResourceManager manager, ProfilerFiller profiler);

	@Invoker("apply")
	void couchcraft$apply(Optional<CloudRenderer.TextureData> preparations, ResourceManager manager, ProfilerFiller profiler);
}
