package dev.couchcraft.mixin.access;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.resources.WaypointStyleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface HudAccessor {
	@Accessor("waypointStyles")
	WaypointStyleManager couchcraft$waypointStyles();

	@Mutable
	@Accessor("waypointStyles")
	void couchcraft$setWaypointStyles(WaypointStyleManager manager);
}
