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

	@Inject(method = "setup", at = @At("HEAD"), cancellable = true)
	private void civcraft$applyIsometricView(Level area,
	                                         Entity focused,
	                                         boolean thirdPerson,
	                                         boolean thirdPersonReverse,
	                                         float partialTicks,
	                                         CallbackInfo ci) {
		if (!TopDownMode.active) return;
		ci.cancel();

		float pitch = TopDownMode.pitch;
		float yaw = TopDownMode.yaw;
		float dist = TopDownMode.distance;

		double radPitch = Math.toRadians(pitch);
		double radYaw = Math.toRadians(yaw);

		// Center on the anchor (not the player), so WASD moves the camera alone.
		double cx = TopDownMode.anchorX;
		double cy = TopDownMode.anchorY;
		double cz = TopDownMode.anchorZ;

		double horiz = Math.cos(radPitch) * dist;
		double vert  = Math.sin(radPitch) * dist;
		double ox = -Math.sin(radYaw) * horiz;
		double oz =  Math.cos(radYaw) * horiz;

		this.setRotation(yaw, pitch);
		this.setPosition(cx + ox, cy + vert, cz + oz);
	}
}
