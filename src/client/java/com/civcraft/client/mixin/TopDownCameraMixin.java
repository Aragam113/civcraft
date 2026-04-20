package com.civcraft.client.mixin;

import com.civcraft.client.camera.TopDownMode;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class TopDownCameraMixin {

	@Shadow protected abstract void setPosition(double x, double y, double z);
	@Shadow protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "setup", at = @At("TAIL"))
	private void civcraft$applyIsometricView(Level area,
	                                         Entity focused,
	                                         boolean thirdPerson,
	                                         boolean thirdPersonReverse,
	                                         float partialTicks,
	                                         CallbackInfo ci) {
		if (!TopDownMode.active) return;

		// Interpolate prev → current using the render-frame's partialTicks so
		// motion stays smooth between 20Hz input ticks.
		float t = partialTicks;
		float pitch = TopDownMode.lerp(TopDownMode.prevPitch, TopDownMode.pitch, t);
		float yaw   = TopDownMode.lerpYaw(TopDownMode.prevYaw, TopDownMode.yaw, t);
		float dist  = TopDownMode.lerp(TopDownMode.prevDistance, TopDownMode.distance, t);

		double radPitch = Math.toRadians(pitch);
		double radYaw = Math.toRadians(yaw);

		double cx = TopDownMode.lerp(TopDownMode.prevAnchorX, TopDownMode.anchorX, t);
		double cy = TopDownMode.lerp(TopDownMode.prevAnchorY, TopDownMode.anchorY, t);
		double cz = TopDownMode.lerp(TopDownMode.prevAnchorZ, TopDownMode.anchorZ, t);

		double horiz = Math.cos(radPitch) * dist;
		double vert  = Math.sin(radPitch) * dist;
		double ox = -Math.sin(radYaw) * horiz;
		double oz =  Math.cos(radYaw) * horiz;

		this.setRotation(yaw, pitch);
		this.setPosition(cx + ox, cy + vert, cz + oz);
	}
}
