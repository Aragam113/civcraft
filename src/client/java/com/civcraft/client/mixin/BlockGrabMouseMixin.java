package com.civcraft.client.mixin;

import com.civcraft.client.camera.TopDownMode;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the RTS camera is on, MC would try to grab the cursor on every click
 * (which recenters the pointer). Block that entirely in iso mode.
 */
@Mixin(MouseHandler.class)
public abstract class BlockGrabMouseMixin {
	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void civcraft$preventGrabInIso(CallbackInfo ci) {
		if (TopDownMode.active) {
			ci.cancel();
		}
	}
}
