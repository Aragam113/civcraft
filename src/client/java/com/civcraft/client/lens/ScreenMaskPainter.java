package com.civcraft.client.lens;

import com.civcraft.client.territory.ClientTownHalls;
import com.civcraft.territory.TownHallEntry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.UUID;

/**
 * Paints the lens mask in screen space. The lens mode picks the colouring
 * rule for a given owned column:
 *
 * <ul>
 *   <li>{@code CITY}  — only the local player's halls; each hall gets a
 *       colour from {@link #CITY_PALETTE} indexed by {@code townHallId},
 *       so city #1 is always yellow, #2 green, and so on.</li>
 *   <li>{@code STATE} — every player's territory; the colour comes from
 *       {@link #STATE_PALETTE} indexed by a stable hash of the player's
 *       UUID.</li>
 * </ul>
 *
 * <p>The shader multiplies the scene by the tint (scaled) so a white
 * palette entry leaves the scene unchanged while coloured entries cast
 * a visible, territory-distinguishing hue without washing out textures.
 */
public final class ScreenMaskPainter {
	private static final int RADIUS_XZ = 192;
	private static final int NEAR_RADIUS_SQ = 32 * 32;
	private static final int MID_RADIUS_SQ  = 64 * 64;
	private static final int NEAR_Y_BELOW = 6;
	private static final int NEAR_Y_ABOVE = 10;
	private static final int MID_Y_BELOW  = 2;
	private static final int MID_Y_ABOVE  = 4;
	private static final float NEAR_W = 0.3f;

	/** Per-city palette for the CITY lens — pastel colours so the shader's
	 *  multiplicative tint reads as a gentle hue shift rather than a neon
	 *  wash. ABGR with full alpha, cycled by {@code (townHallId−1) mod N}. */
	private static final int[] CITY_PALETTE = {
			0xFFA0E8F4, // #1 soft yellow      RGB 244,232,160
			0xFFA8DDB4, // #2 sage green       RGB 180,221,168
			0xFFECC89B, // #3 powder blue      RGB 155,200,236
			0xFFE8B8D0, // #4 lavender         RGB 208,184,232
			0xFFDCE8BC, // #5 soft teal        RGB 188,232,220
			0xFFE8EFF0, // #6 cream            RGB 240,239,232
			0xFF90B8F4, // #7 peach            RGB 244,184,144
			0xFFAAB8E8, // #8 dusty rose       RGB 232,184,170
			0xFFC4F0B4, // #9 mint             RGB 180,240,196
			0xFFC4BCE8, // #10 heather
	};

	/** Per-player palette for the STATE lens — muted civ colours so two
	 *  neighbouring nations read as clearly distinct without fighting each
	 *  other for attention. */
	private static final int[] STATE_PALETTE = {
			0xFF9898E8, // dusty red
			0xFFE8A890, // cornflower blue
			0xFF98E898, // fern green
			0xFF90D8E8, // mustard yellow
			0xFFE898E0, // soft magenta
			0xFFE8E8A0, // pale cyan
			0xFFB0BCE8, // coral
			0xFFE8C8B0, // azure
	};

	/** SETTLER lens palette: tint used for the "can't settle here" band. */
	private static final int SETTLER_RED  = 0xFF7878F0; // soft red, ABGR
	/** SETTLER lens palette: tint used for "open wilderness" — white keeps
	 *  the scene fully coloured under the shader's multiplicative mix. */
	private static final int SETTLER_FREE = 0xFFFFFFFF;

	/** Buffer distances (blocks) past any hall's claim edge where a new
	 *  hall may not be placed. Mirrors {@code GhostValidator.FOREIGN_BUFFER}
	 *  and {@code OWN_BUFFER} so the lens matches the real rules. */
	private static final int SETTLER_FOREIGN_BUFFER = 28;
	private static final int SETTLER_OWN_BUFFER     = 18;

	private ScreenMaskPainter() {}

	public static void paint(NativeImage out, Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		LensState.Mode mode = LensState.mode;
		if (mode == LensState.Mode.NONE) return;
		UUID myUUID = mc.player.getUUID();
		Camera cam = mc.gameRenderer.getMainCamera();
		Vec3 camPos = cam.position();

		// Online players set — used to ignore orphan halls the same way
		// the GhostValidator does so the lens matches the real rules.
		java.util.Set<UUID> online = new java.util.HashSet<>();
		var conn = mc.getConnection();
		if (conn != null) {
			for (var info : conn.getOnlinePlayers()) online.add(info.getProfile().id());
		}
		int claimSq = TownHallEntry.CLAIM_RADIUS * TownHallEntry.CLAIM_RADIUS;
		int foreignBufferSq = (TownHallEntry.CLAIM_RADIUS + SETTLER_FOREIGN_BUFFER)
				* (TownHallEntry.CLAIM_RADIUS + SETTLER_FOREIGN_BUFFER);
		int ownBufferSq     = (TownHallEntry.CLAIM_RADIUS + SETTLER_OWN_BUFFER)
				* (TownHallEntry.CLAIM_RADIUS + SETTLER_OWN_BUFFER);

		int mw = out.getWidth();
		int mh = out.getHeight();
		for (int y = 0; y < mh; y++) {
			for (int x = 0; x < mw; x++) {
				out.setPixelABGR(x, y, 0xFF000000);
			}
		}

		int ww = mc.getWindow().getWidth();
		int wh = mc.getWindow().getHeight();
		float fov = (float) mc.options.fov().get().intValue();
		float aspect = (float) ww / (float) wh;
		Matrix4f projection = new Matrix4f().perspective(
				(float) Math.toRadians(fov), aspect, 0.05f, 1000f);
		Quaternionf invRot = new Quaternionf(cam.rotation()).invert();
		Matrix4f view = new Matrix4f()
				.rotate(invRot)
				.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
		Matrix4f vp = projection.mul(view);
		Vector4f clip = new Vector4f();
		float[] qx = new float[4];
		float[] qy = new float[4];

		Level level = mc.level;
		int camX = (int) Math.floor(camPos.x);
		int camZ = (int) Math.floor(camPos.z);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -RADIUS_XZ; dx <= RADIUS_XZ; dx++) {
			for (int dz = -RADIUS_XZ; dz <= RADIUS_XZ; dz++) {
				int wx = camX + dx;
				int wz = camZ + dz;
				int colorABGR;
				if (mode == LensState.Mode.SETTLER) {
					// Walk halls once: detect "owned by a real player" OR
					// "inside any real player's buffer disc". Orphans (owner
					// offline and not me) are ignored entirely.
					boolean owned = false;
					boolean inBuffer = false;
					for (TownHallEntry th : ClientTownHalls.all()) {
						boolean mine = th.playerId().equals(myUUID);
						boolean real = mine || online.contains(th.playerId());
						if (!real) continue;
						int ddx = wx - th.pos().getX();
						int ddz = wz - th.pos().getZ();
						int d2 = ddx * ddx + ddz * ddz;
						if (d2 <= claimSq) { owned = true; break; }
						int bufSq = mine ? ownBufferSq : foreignBufferSq;
						if (d2 < bufSq) inBuffer = true;
					}
					if (owned) continue;  // stays black → shader desaturates
					colorABGR = inBuffer ? SETTLER_RED : SETTLER_FREE;
				} else {
					TownHallEntry owner = ClientTownHalls.owningHall(wx, wz);
					if (owner == null) continue;
					if (mode == LensState.Mode.CITY) {
						if (!owner.playerId().equals(myUUID)) continue;
						colorABGR = cityColor(owner.townHallId());
					} else {
						colorABGR = stateColor(owner.playerId());
					}
				}

				int dist2 = dx * dx + dz * dz;
				int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
				int yLo, yHi;
				if (dist2 <= NEAR_RADIUS_SQ) {
					yLo = surfaceY - NEAR_Y_BELOW;
					yHi = surfaceY + NEAR_Y_ABOVE;
				} else if (dist2 <= MID_RADIUS_SQ) {
					yLo = surfaceY - MID_Y_BELOW;
					yHi = surfaceY + MID_Y_ABOVE;
				} else {
					yLo = surfaceY - 1;
					yHi = surfaceY + 1;
				}
				for (int wy = yLo; wy < yHi; wy++) {
					p.set(wx, wy, wz);
					BlockState st = level.getBlockState(p);
					if (st.isAir()) continue;

					if (camPos.x > wx + 1) paintFace(out, vp, mw, mh, clip, qx, qy, colorABGR,
							wx + 1, wy,     wz,
							wx + 1, wy + 1, wz,
							wx + 1, wy + 1, wz + 1,
							wx + 1, wy,     wz + 1);
					if (camPos.x < wx)     paintFace(out, vp, mw, mh, clip, qx, qy, colorABGR,
							wx, wy,     wz,
							wx, wy,     wz + 1,
							wx, wy + 1, wz + 1,
							wx, wy + 1, wz);
					if (camPos.y > wy + 1) paintFace(out, vp, mw, mh, clip, qx, qy, colorABGR,
							wx,     wy + 1, wz,
							wx,     wy + 1, wz + 1,
							wx + 1, wy + 1, wz + 1,
							wx + 1, wy + 1, wz);
					if (camPos.y < wy)     paintFace(out, vp, mw, mh, clip, qx, qy, colorABGR,
							wx,     wy, wz,
							wx + 1, wy, wz,
							wx + 1, wy, wz + 1,
							wx,     wy, wz + 1);
					if (camPos.z > wz + 1) paintFace(out, vp, mw, mh, clip, qx, qy, colorABGR,
							wx,     wy,     wz + 1,
							wx + 1, wy,     wz + 1,
							wx + 1, wy + 1, wz + 1,
							wx,     wy + 1, wz + 1);
					if (camPos.z < wz)     paintFace(out, vp, mw, mh, clip, qx, qy, colorABGR,
							wx,     wy,     wz,
							wx,     wy + 1, wz,
							wx + 1, wy + 1, wz,
							wx + 1, wy,     wz);
				}
			}
		}
	}

	private static int cityColor(int townHallId) {
		int idx = Math.floorMod(townHallId - 1, CITY_PALETTE.length);
		return CITY_PALETTE[idx];
	}

	private static int stateColor(UUID playerId) {
		int idx = Math.floorMod(playerId.hashCode(), STATE_PALETTE.length);
		return STATE_PALETTE[idx];
	}

	private static void paintFace(NativeImage out, Matrix4f vp, int mw, int mh,
	                              Vector4f clip, float[] qx, float[] qy, int colorABGR,
	                              double p0x, double p0y, double p0z,
	                              double p1x, double p1y, double p1z,
	                              double p2x, double p2y, double p2z,
	                              double p3x, double p3y, double p3z) {
		double[] px = {p0x, p1x, p2x, p3x};
		double[] py = {p0y, p1y, p2y, p3y};
		double[] pz = {p0z, p1z, p2z, p3z};
		for (int i = 0; i < 4; i++) {
			clip.set((float) px[i], (float) py[i], (float) pz[i], 1f);
			vp.transform(clip);
			if (clip.w <= NEAR_W) return;
			qx[i] = (clip.x / clip.w + 1f) * 0.5f * mw;
			qy[i] = (clip.y / clip.w + 1f) * 0.5f * mh;
			if (qx[i] < -mw || qx[i] > mw * 2 || qy[i] < -mh || qy[i] > mh * 2) return;
		}
		float minY = Math.min(Math.min(qy[0], qy[1]), Math.min(qy[2], qy[3]));
		float maxY = Math.max(Math.max(qy[0], qy[1]), Math.max(qy[2], qy[3]));
		int y0 = Math.max(0, (int) Math.floor(minY));
		int y1 = Math.min(mh - 1, (int) Math.ceil(maxY));
		for (int y = y0; y <= y1; y++) {
			float yf = y + 0.5f;
			float lx = Float.POSITIVE_INFINITY;
			float rx = Float.NEGATIVE_INFINITY;
			for (int i = 0; i < 4; i++) {
				int j = (i + 1) & 3;
				float a = qy[i], bB = qy[j];
				if ((a <= yf && bB > yf) || (bB <= yf && a > yf)) {
					float t = (yf - a) / (bB - a);
					float ix = qx[i] + t * (qx[j] - qx[i]);
					if (ix < lx) lx = ix;
					if (ix > rx) rx = ix;
				}
			}
			if (lx == Float.POSITIVE_INFINITY) continue;
			int xl = Math.max(0, (int) Math.floor(lx));
			int xr = Math.min(mw - 1, (int) Math.ceil(rx));
			for (int x = xl; x <= xr; x++) {
				out.setPixelABGR(x, y, colorABGR);
			}
		}
	}
}
