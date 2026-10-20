package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.PlayerContext;
import dev.couchcraft.context.VirtualWindow;
import dev.couchcraft.render.CouchHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class GuiMixin {
	/** Draw Couchcraft's overlay on top of everything; only the keyboard player may change the OS cursor shape. */
	@WrapOperation(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V")
	)
	private void couchcraft$overlay(final GuiGraphicsExtractor graphics, final Window window, final Operation<Void> original) {
		CouchHud.extract(graphics);
		if (!Couch.isGuestActive()) {
			original.call(graphics, window);
		}
	}

	/**
	 * Safety net: a screen is always opened inside its owner's scope, so it is laid out for that player's viewport
	 * no matter which code path (task, callback, other mod) opened it.
	 */
	@WrapMethod(method = "setScreen")
	private void couchcraft$openInOwnersViewport(final @Nullable Screen screen, final Operation<Void> original) {
		if (Couch.isSplit() && VirtualWindow.active() == null) {
			for (PlayerContext context : Couch.contexts()) {
				if (context.gui == (Object)this) {
					try (Couch.Scope ignored = Couch.enter(context)) {
						original.call(screen);
					}

					return;
				}
			}
		}

		original.call(screen);
	}
}
