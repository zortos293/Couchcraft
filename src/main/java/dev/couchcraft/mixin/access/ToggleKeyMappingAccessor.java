package dev.couchcraft.mixin.access;

import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ToggleKeyMapping.class)
public interface ToggleKeyMappingAccessor {
	@Accessor("releasedByScreenWhenDown")
	boolean couchcraft$releasedByScreen();

	@Accessor("releasedByScreenWhenDown")
	void couchcraft$setReleasedByScreen(boolean value);
}
