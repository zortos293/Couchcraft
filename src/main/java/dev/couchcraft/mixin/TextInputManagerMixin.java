package dev.couchcraft.mixin;

import com.mojang.blaze3d.platform.TextInputManager;
import dev.couchcraft.context.Couch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The OS text-input (IME) session belongs to the keyboard, which belongs to P1. Guests' text fields must not steal it. */
@Mixin(TextInputManager.class)
public abstract class TextInputManagerMixin {
	@Inject(
		method = {"setTextInputArea", "startTextInput", "stopTextInput(Ljava/lang/Object;)V", "stopTextInput()V", "onTextInputFocusChange"},
		at = @At("HEAD"),
		cancellable = true
	)
	private void couchcraft$keyboardIsP1s(final CallbackInfo ci) {
		if (Couch.isGuestActive()) {
			ci.cancel();
		}
	}
}
