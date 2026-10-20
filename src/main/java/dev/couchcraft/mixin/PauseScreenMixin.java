package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.couchcraft.gui.CouchScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds "Couch Co-op…" to the pause menu, just above Save and Quit. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
	protected PauseScreenMixin(final Component title) {
		super(title);
	}

	@Inject(
		method = "createPauseMenu",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/CommonComponents;disconnectButtonLabel(Z)Lnet/minecraft/network/chat/Component;")
	)
	private void couchcraft$addCouchButton(final CallbackInfo ci, @Local final GridLayout.RowHelper helper) {
		helper.addChild(
			Button.builder(
					Component.translatableWithFallback("couchcraft.screen.open", "Couch Co-op…"),
					button -> this.minecraft.gui.setScreen(new CouchScreen(this))
				)
				.width(204)
				.build(),
			2
		);
	}
}
