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
		int fbW = mc.getWindow().getWidth();
		int fbH = mc.getWindow().getHeight();
		double anchorY = TopDownMode.anchorY;

		for (int py = 0; py < MASK_H; py++) {
			for (int px = 0; px < MASK_W; px++) {
				// The vanilla screenquad vertex shader emits UV (0..1) across
				// the visible window; sampler coordinate (u, v) with v=0 at
				// the BOTTOM maps to NativeImage row (MASK_H - 1) — so we
				// write with an inverted row index to cancel the Y-flip.
				double fbX = (px + 0.5) * fbW / (double) MASK_W;
				double fbY = (py + 0.5) * fbH / (double) MASK_H;
				Vec3 ground = CameraMath.cursorToGround(mc, fbX, fbY, anchorY);
				boolean owned = false;
				if (ground != null) {
					for (BlockPos h : halls) {
						double dx = ground.x - h.getX();
						double dz = ground.z - h.getZ();
						if (dx * dx + dz * dz <= rSq) { owned = true; break; }
					}
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
