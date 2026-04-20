package com.civcraft.client.mixin;

import com.civcraft.client.camera.TopDownMode;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercept mouse wheel while iso mode is active and use it for camera zoom
 * instead of the usual hotbar slot scroll.
 */
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void civcraft$zoom(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (!TopDownMode.active) return;
		double step = 2.0;
		TopDownMode.distance -= yOffset * step;
		TopDownMode.distance = Math.max(8.0f, Math.min(80.0f, (float) TopDownMode.distance));
		ci.cancel();
	}
}
