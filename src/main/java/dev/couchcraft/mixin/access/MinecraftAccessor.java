package dev.couchcraft.mixin.access;

import com.mojang.renderpearl.api.device.GpuSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Mutable
	@Accessor("gameRenderer")
	void couchcraft$setGameRenderer(GameRenderer gameRenderer);

	@Mutable
	@Accessor("levelRenderer")
	void couchcraft$setLevelRenderer(LevelRenderer levelRenderer);

	@Mutable
	@Accessor("levelExtractor")
	void couchcraft$setLevelExtractor(LevelExtractor levelExtractor);

	@Mutable
	@Accessor("particleEngine")
	void couchcraft$setParticleEngine(ParticleEngine particleEngine);

	@Mutable
	@Accessor("gui")
	void couchcraft$setGui(Gui gui);

	@Mutable
	@Accessor("mouseHandler")
	void couchcraft$setMouseHandler(MouseHandler mouseHandler);

	@Accessor("rightClickDelay")
	int couchcraft$rightClickDelay();

	@Accessor("rightClickDelay")
	void couchcraft$setRightClickDelay(int delay);

	@Accessor("missTime")
	int couchcraft$missTime();

	@Accessor("missTime")
	void couchcraft$setMissTime(int missTime);

	@Accessor("pendingConnection")
	Connection couchcraft$pendingConnection();

	@Accessor("pendingConnection")
	void couchcraft$setPendingConnection(Connection connection);

	@Accessor("windowSurface")
	GpuSurface couchcraft$windowSurface();

	@Invoker("pick")
	void couchcraft$pick(float partialTicks);
}
