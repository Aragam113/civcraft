package com.civcraft.client.camera;

/**
 * Client-side state for the isometric camera.
 *
 * {@link #anchorX}/{@link #anchorY}/{@link #anchorZ} is the world-space point
 * the camera is framing — moved by WASD or MMB drag. The {@code prev*} fields
 * are the values at the start of the current client tick; the camera mixin
 * lerps between them using partialTicks to give frame-smooth motion even
 * though input only updates at 20 Hz.
 */
public final class TopDownMode {
	public static boolean active = false;

	// Current tick values.
	public static float pitch = 60.0f;
	public static float yaw = 45.0f;
	public static float distance = 20.0f;
	public static double anchorX = 0;
	public static double anchorY = 80;
	public static double anchorZ = 0;

	// Previous tick values — snapshotted at the start of each client tick.
	public static float prevPitch = 60.0f;
	public static float prevYaw = 45.0f;
	public static float prevDistance = 20.0f;
	public static double prevAnchorX = 0;
	public static double prevAnchorY = 80;
	public static double prevAnchorZ = 0;

	public static double panSpeed = 0.6;         // blocks/tick via WASD
	public static double mousePanSensitivity = 0.035;  // world blocks per screen pixel when MMB-dragging
	public static float mouseYawSensitivity = 0.25f;   // degrees per pixel when Shift+MMB-dragging

	private TopDownMode() {}

	public static void toggle() {
		active = !active;
	}

	/** Copy current -> prev. Call at the start of each client tick. */
	public static void snapshot() {
		prevAnchorX = anchorX;
		prevAnchorY = anchorY;
		prevAnchorZ = anchorZ;
		prevYaw = yaw;
		prevPitch = pitch;
		prevDistance = distance;
	}

	public static double lerp(double prev, double cur, float t) {
		return prev + (cur - prev) * t;
	}
	public static float lerp(float prev, float cur, float t) {
		return prev + (cur - prev) * t;
	}

	/** Shortest-arc yaw lerp so 359° → 0° doesn't spin the long way. */
	public static float lerpYaw(float prev, float cur, float t) {
		float diff = ((cur - prev) % 360f + 540f) % 360f - 180f;
		return prev + diff * t;
	}
}
