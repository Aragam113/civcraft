package com.civcraft.client.render;

import com.civcraft.client.camera.CameraMath;
import com.civcraft.client.camera.TopDownMode;
import com.civcraft.client.lens.LensState;
import com.civcraft.registry.ModBlocks;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Desaturation post-process for the lens feature. Runs each frame when the
 * lens is active:
 *   1. Fills a greyscale territory mask — one pixel per mask-texel, sampled
 *      through the camera's inverse projection so it matches the rendered
 *      scene.
 *   2. Fetches the compiled PostChain from the vanilla ShaderManager.
 *   3. Calls chain.process(main_target, UNPOOLED), which runs the two-pass
 *      JSON config: desaturate (reading mask) → blit back to main.
 */
public final class LensPostEffect {
	private static final Identifier POST_CHAIN_ID =
			Identifier.fromNamespaceAndPath("civcraft", "civcraft_lens");
	public  static final Identifier MASK_TEXTURE_ID =
			Identifier.fromNamespaceAndPath("civcraft", "territory_mask");

	private static final int MASK_W = 256;
	private static final int MASK_H = 256;

	private static NativeImage maskImage;
	private static DynamicTexture maskTexture;
	private static boolean textureRegistered = false;

	// Town-hall position cache, refreshed at a lower rate than rendering.
	private static List<BlockPos> cachedHalls = Collections.emptyList();
	private static long lastScan = 0;
	private static double scanAnchorX, scanAnchorZ;

	private LensPostEffect() {}

	public static void register() {
		WorldRenderEvents.END_MAIN.register(ctx -> apply());
	}

	private static void apply() {
		if (LensState.mode == LensState.Mode.NONE) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) return;

		ensureMaskTexture(mc);
		fillMask(mc, LensState.mode);
		maskTexture.upload();

		PostChain chain;
		try {
			// externalTargets declares which RENDER TARGETS the chain references
			// from outside (not textures — those are resolved via TextureManager).
			// Our chain reads + writes minecraft:main, so that must be listed.
			chain = mc.getShaderManager().getPostChain(POST_CHAIN_ID,
					java.util.Set.of(PostChain.MAIN_TARGET_ID));
		} catch (Throwable t) {
			return;
		}
		if (chain == null) return;

		chain.process(mc.getMainRenderTarget(), GraphicsResourceAllocator.UNPOOLED);
	}

	// ─── Territory mask ───────────────────────────────────────────────────────

	private static void ensureMaskTexture(Minecraft mc) {
		if (maskImage == null) {
			maskImage = new NativeImage(NativeImage.Format.RGBA, MASK_W, MASK_H, false);
		}
		if (maskTexture == null) {
			maskTexture = new DynamicTexture(() -> "civcraft_territory_mask", maskImage);
		}
		if (!textureRegistered) {
			mc.getTextureManager().register(MASK_TEXTURE_ID, maskTexture);
			textureRegistered = true;
		}
	}

	private static void fillMask(Minecraft mc, LensState.Mode mode) {
		int radius = mode.radius();
		int rSq = radius * radius;
		List<BlockPos> halls = nearbyHalls(mc);

		// Simple orthographic approximation for a top-down camera: the visible
		// ground-plane footprint spans roughly (distance * 1.6) blocks wide
		// and (distance * 0.9) deep. This is good enough for a mask — we
		// don't need millimetre accuracy, and unlike the perspective-ray
		// method it never returns null for edge-of-screen pixels (which
		// is what was producing the chess-pattern artefact).
		double dist    = TopDownMode.distance;
		double spanX   = dist * 1.60;  // half-width in world blocks
		double spanZ   = dist * 0.90;  // half-depth in world blocks (pitched perspective squash)
		double yawRad  = Math.toRadians(TopDownMode.yaw);
		double cosY    = Math.cos(yawRad);
		double sinY    = Math.sin(yawRad);
		double anchorX = TopDownMode.anchorX;
		double anchorZ = TopDownMode.anchorZ;

		for (int py = 0; py < MASK_H; py++) {
			// py=0 is the mask's TOP (screen-top in our conventions).
			// UV-space: screen TOP  → texture v=1 → NativeImage row 0 (uploaded top-down but sampled bottom-up).
			// Easiest: compute everything in screen-space then flip the Y
			// when storing into the NativeImage row so the GL sampler sees
			// the right thing.
			double ndcY = 1.0 - (py + 0.5) / (double) MASK_H * 2.0; // +1 top, -1 bottom
			double localZ = -ndcY * spanZ;   // screen UP = world NORTH = -Z
			for (int px = 0; px < MASK_W; px++) {
				double ndcX = (px + 0.5) / (double) MASK_W * 2.0 - 1.0; // -1 left, +1 right
				double localX = ndcX * spanX;

				// Rotate the local (east, south) offset by yaw around Y.
				double wx = anchorX + localX * cosY - localZ * sinY;
				double wz = anchorZ + localX * sinY + localZ * cosY;

				boolean owned = false;
				for (BlockPos h : halls) {
					double dx = wx - h.getX();
					double dz = wz - h.getZ();
					if (dx * dx + dz * dz <= rSq) { owned = true; break; }
				}

				int v = owned ? 255 : 0;
				int abgr = 0xFF000000 | (v << 16) | (v << 8) | v;
				maskImage.setPixelABGR(px, MASK_H - 1 - py, abgr);
			}
		}
	}

	private static List<BlockPos> nearbyHalls(Minecraft mc) {
		long now = System.currentTimeMillis();
		double dx = TopDownMode.anchorX - scanAnchorX;
		double dz = TopDownMode.anchorZ - scanAnchorZ;
		if (now - lastScan < 800 && dx * dx + dz * dz < 16) return cachedHalls;
		if (mc.level == null) return cachedHalls;

		int scanR = 192;
		int cx = (int) TopDownMode.anchorX;
		int cz = (int) TopDownMode.anchorZ;
		List<BlockPos> out = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = cx - scanR; x <= cx + scanR; x += 2) {
			for (int z = cz - scanR; z <= cz + scanR; z += 2) {
				int gy = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				for (int dy = -2; dy <= 10; dy++) {
					m.set(x, gy + dy, z);
					if (mc.level.getBlockState(m).getBlock() == ModBlocks.TOWN_HALL) {
						out.add(m.immutable());
						break;
					}
				}
			}
		}
		cachedHalls = out;
		lastScan = now;
		scanAnchorX = TopDownMode.anchorX;
		scanAnchorZ = TopDownMode.anchorZ;
		return out;
	}
}
