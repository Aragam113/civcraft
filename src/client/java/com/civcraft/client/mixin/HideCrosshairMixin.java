package com.civcraft.client.mixin;

import com.civcraft.client.camera.TopDownMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppress the vanilla crosshair while the RTS camera is active. We draw our
 * own cursor on top of the HUD instead.
 */
@Mixin(Gui.class)
public abstract class HideCrosshairMixin {

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void civcraft$maybeHideCrosshair(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
		if (TopDownMode.active) {
			ci.cancel();
		}
	}
}
