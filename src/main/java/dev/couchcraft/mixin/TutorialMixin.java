package dev.couchcraft.mixin;

import dev.couchcraft.context.Couch;
import net.minecraft.client.tutorial.Tutorial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The tutorial belongs to the account owner; guests' actions must not advance or reset it. */
@Mixin(Tutorial.class)
public abstract class TutorialMixin {
	@Inject(
		method = {"onInput", "onMouse", "onLookAt", "onDestroyBlock", "onOpenInventory", "onGetItem", "tick", "onInventoryAction"},
		at = @At("HEAD"),
		cancellable = true
	)
	private void couchcraft$hostOnly(final CallbackInfo ci) {
		if (Couch.isGuestActive()) {
			ci.cancel();
		}
	}
}
