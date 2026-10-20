package dev.couchcraft.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.couchcraft.context.VirtualWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a player's scope is active, the window reports that player's viewport. The real fields are untouched,
 * so window events (resize, fullscreen) always operate on the real window.
 */
@Mixin(Window.class)
public abstract class WindowMixin {
	@Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
	private void couchcraft$width(final CallbackInfoReturnable<Integer> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null) {
			cir.setReturnValue(frame.framebufferWidth());
		}
	}

	@Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
	private void couchcraft$height(final CallbackInfoReturnable<Integer> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null) {
			cir.setReturnValue(frame.framebufferHeight());
		}
	}

	@Inject(method = "getScreenWidth", at = @At("HEAD"), cancellable = true)
	private void couchcraft$screenWidth(final CallbackInfoReturnable<Integer> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null) {
			cir.setReturnValue(frame.screenWidth());
		}
	}

	@Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true)
	private void couchcraft$screenHeight(final CallbackInfoReturnable<Integer> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null) {
			cir.setReturnValue(frame.screenHeight());
		}
	}

	@Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guiWidth(final CallbackInfoReturnable<Integer> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null) {
			cir.setReturnValue(frame.guiScaledWidth());
		}
	}

	@Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guiHeight(final CallbackInfoReturnable<Integer> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null) {
			cir.setReturnValue(frame.guiScaledHeight());
		}
	}

	@Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guiScale(final CallbackInfoReturnable<Integer> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null) {
			cir.setReturnValue(frame.guiScale());
		}
	}

	@Inject(method = "calculateScale", at = @At("HEAD"), cancellable = true)
	private void couchcraft$calculateScale(final int maxScale, final boolean enforceUnicode, final CallbackInfoReturnable<Integer> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null) {
			cir.setReturnValue(VirtualWindow.Frame.calculateScale(frame.framebufferWidth(), frame.framebufferHeight(), maxScale, enforceUnicode));
		}
	}
}
