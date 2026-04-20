package com.civcraft.client.camera;

/**
 * Client-side state for the isometric camera.
 *
 * {@link #anchor*} is the world-space point that the camera is framing; it is
 * moved around by WASD while iso mode is active. The player entity sits at
 * this anchor but is put into spectator so the world passes through them.
 */
public final class TopDownMode {
	public static boolean active = false;

	// View parameters.
	public static float pitch = 60.0f;
	public static float yaw = 45.0f;
	public static float distance = 20.0f;

	// Where the camera is currently looking. Initialised from the player
	// position on activation, then scrolled by WASD.
	public static double anchorX = 0;
	public static double anchorY = 80;
	public static double anchorZ = 0;

	// How fast WASD pans the anchor, blocks per tick.
	public static double panSpeed = 0.6;

	// Yaw step per key press when rotating with Z / X.
	public static float yawStep = 5.0f;

	private TopDownMode() {}

	public static void toggle() {
		active = !active;
	}
}
