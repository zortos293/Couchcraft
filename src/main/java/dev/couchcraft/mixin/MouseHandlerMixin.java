package dev.couchcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.couchcraft.context.Couch;
import dev.couchcraft.context.VirtualWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two kinds of mouse handler exist on the couch:
 * <ul>
 *   <li>the real one (P1), whose position is in window coordinates and is remapped into P1's viewport;</li>
 *   <li>guests' virtual ones, driven by their controller cursor in viewport coordinates, which must never touch the OS cursor.</li>
 * </ul>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

	private boolean couchcraft$isReal() {
		return !Couch.ready() || Couch.primary().mouseHandler == (Object)this;
	}

	@Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
	private void couchcraft$scaledX(final Window window, final CallbackInfoReturnable<Double> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null && this.couchcraft$isReal()) {
			cir.setReturnValue((this.xpos - frame.screenX()) * frame.guiScaledWidth() / frame.screenWidth());
		}
	}

	@Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
	private void couchcraft$scaledY(final Window window, final CallbackInfoReturnable<Double> cir) {
		VirtualWindow.Frame frame = VirtualWindow.active();
		if (frame != null && this.couchcraft$isReal()) {
			cir.setReturnValue((this.ypos - frame.screenY()) * frame.guiScaledHeight() / frame.screenHeight());
		}
	}

	@WrapOperation(
		method = "grabMouse",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabMouse(Lcom/mojang/blaze3d/platform/Window;DD)V")
	)
	private void couchcraft$grab(final Window window, final double x, final double y, final Operation<Void> original) {
		if (this.couchcraft$isReal()) {
			VirtualWindow.Frame frame = VirtualWindow.active();
			double offsetX = frame == null ? 0.0 : frame.screenX();
			double offsetY = frame == null ? 0.0 : frame.screenY();
			this.xpos = x + offsetX;
			this.ypos = y + offsetY;
			original.call(window, this.xpos, this.ypos);
		}
	}

	@WrapOperation(
		method = "releaseMouse",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V")
	)
	private void couchcraft$release(final Window window, final double x, final double y, final Operation<Void> original) {
		if (this.couchcraft$isReal()) {
			// Drop the freed cursor in the middle of P1's own viewport, not the middle of the window.
			VirtualWindow.Frame frame = VirtualWindow.active();
			double offsetX = frame == null ? 0.0 : frame.screenX();
			double offsetY = frame == null ? 0.0 : frame.screenY();
			this.xpos = x + offsetX;
			this.ypos = y + offsetY;
			original.call(window, this.xpos, this.ypos);
		}
	}

	/** Controllers keep working when the window is unfocused, so a guest's virtual mouse doesn't care about focus. */
	@WrapOperation(
		method = {"grabMouse", "onMove", "handleAccumulatedMovement"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isWindowActive()Z")
	)
	private boolean couchcraft$guestsIgnoreFocus(final Minecraft instance, final Operation<Boolean> original) {
		return !this.couchcraft$isReal() || original.call(instance);
	}

	@Inject(method = "resyncMousePosition", at = @At("HEAD"), cancellable = true)
	private void couchcraft$guestsHaveNoOsCursor(final CallbackInfo ci) {
		if (!this.couchcraft$isReal()) {
			ci.cancel();
		}
	}
}
