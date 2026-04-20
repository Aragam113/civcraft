package com.civcraft.client.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Screen ↔ world math for the RTS camera. Produces a ground-plane intersection
 * from a window-space mouse coordinate, and projects a world point onto the
 * current screen so we can check whether entities sit inside a selection rect.
 *
 * We rebuild the view matrix locally from the camera's pose rather than
 * grabbing Minecraft's render-time matrices, so this works outside of a render
 * pass (e.g. in client tick).
 */
public final class CameraMath {

	private CameraMath() {}

	/** Build (VP) matrix for the current camera and framebuffer size. */
	private static Matrix4f viewProjection(Minecraft mc) {
		Camera cam = mc.gameRenderer.getMainCamera();
		int w = mc.getWindow().getWidth();
		int h = mc.getWindow().getHeight();
		float fov = (float) mc.options.fov().get().intValue();
		float aspect = (float) w / (float) h;

		Matrix4f projection = new Matrix4f().perspective(
				(float) Math.toRadians(fov), aspect, 0.05f, 1000f);

		// View matrix = inverse(rotation) * translation(-pos)
		Quaternionf invRot = new Quaternionf(cam.rotation()).invert();
		Vec3 pos = cam.position();
		Matrix4f view = new Matrix4f()
				.rotate(invRot)
				.translate((float) -pos.x, (float) -pos.y, (float) -pos.z);

		return projection.mul(view);
	}

	/**
	 * @return screen-space (x, y) in window pixels for the given world point, or
	 * null if the point is behind the camera.
	 */
	public static float[] worldToScreen(Minecraft mc, double wx, double wy, double wz) {
		Matrix4f vp = viewProjection(mc);
		Vector4f clip = vp.transform(new Vector4f((float) wx, (float) wy, (float) wz, 1f));
		if (clip.w <= 0) return null;
		float ndcX = clip.x / clip.w;
		float ndcY = clip.y / clip.w;
		float sx = (ndcX + 1f) * 0.5f * mc.getWindow().getWidth();
		float sy = (1f - ndcY) * 0.5f * mc.getWindow().getHeight();
		return new float[]{sx, sy};
	}

	/**
	 * Cast a ray from the cursor and find where it meets y = {@code planeY}.
	 * Returns null if the ray is parallel to the plane or pointing away.
	 */
	public static Vec3 cursorToGround(Minecraft mc, double mouseX, double mouseY, double planeY) {
		Matrix4f vp = viewProjection(mc);
		Matrix4f inv = new Matrix4f(vp).invert();

		int w = mc.getWindow().getWidth();
		int h = mc.getWindow().getHeight();
		float ndcX = (float) (2.0 * mouseX / w - 1.0);
		float ndcY = (float) (1.0 - 2.0 * mouseY / h);

		Vector4f near = inv.transform(new Vector4f(ndcX, ndcY, -1f, 1f));
		Vector4f far  = inv.transform(new Vector4f(ndcX, ndcY,  1f, 1f));
		near.div(near.w);
		far.div(far.w);

		Vector3f origin = new Vector3f(near.x, near.y, near.z);
		Vector3f dir = new Vector3f(far.x - near.x, far.y - near.y, far.z - near.z).normalize();

		if (Math.abs(dir.y) < 1e-4) return null;
		float t = (float) ((planeY - origin.y) / dir.y);
		if (t < 0) return null;
		float x = origin.x + t * dir.x;
		float z = origin.z + t * dir.z;
		return new Vec3(x, planeY, z);
	}
}
