package dev.couchcraft.mixin.access;

import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("ALL")
	static Map<String, KeyMapping> couchcraft$all() {
		throw new AssertionError();
	}

	@Accessor("isDown")
	boolean couchcraft$isDownRaw();

	@Accessor("isDown")
	void couchcraft$setDownRaw(boolean down);

	@Accessor("clickCount")
	int couchcraft$clickCount();

	@Accessor("clickCount")
	void couchcraft$setClickCount(int count);
}
