package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.SDLEventHandler;
import dev.couchcraft.context.Couch;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Keyboard and mouse belong to P1: their events are handled inside P1's scope, so menus see P1's viewport.
 * Window events (resize, focus, quit) are left alone and keep acting on the real window.
 */
@Mixin(SDLEventHandler.class)
public abstract class SDLEventHandlerMixin {
	@WrapMethod(
		method = {
			"handleKeyEvent",
			"handleTextInputEvent",
			"handleTextEditingEvent",
			"handleMouseMotionEvent",
			"handleMouseButtonEvent",
			"handleMouseWheelEvent",
			"handleDropFileEvent"
		}
	)
	private void couchcraft$asPrimary(final SDL_Event event, final Operation<Void> original) {
		Couch.asPrimary(() -> original.call(event));
	}
}
