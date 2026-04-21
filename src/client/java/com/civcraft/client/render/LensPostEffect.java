package com.civcraft.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Stage 1 scaffolding for the lens post-process shader. Owns the grayscale
 * territory-mask texture the fragment shader will read in stage 2, plus the
 * identifiers the chain expects. Actual binding into the LevelRenderer
 * FrameGraph is stage 3.
 *
 * <p>The mask is a screen-resolution grayscale image: 0 = unowned (fragment
 * shader desaturates), 255 = owned (passes through). Re-uploaded each frame
 * when the lens is active.</p>
 */
public final class LensPostEffect {
	public static final Identifier MASK_TEXTURE_ID =
			Identifier.fromNamespaceAndPath("civcraft", "territory_mask");
	public static final Identifier POST_CHAIN_ID =
			Identifier.fromNamespaceAndPath("civcraft", "civcraft_lens");

	private static NativeImage maskImage;
	private static DynamicTexture maskTexture;
	private static boolean registered = false;
	private static int builtW = 0, builtH = 0;

	private LensPostEffect() {}

	/** Ensure the mask texture exists and matches the current framebuffer size. */
	public static DynamicTexture ensureMask(Minecraft mc, int w, int h) {
		if (mc == null) return null;
		if (maskImage == null || builtW != w || builtH != h) {
			if (maskImage != null) maskImage.close();
			maskImage = new NativeImage(NativeImage.Format.RGBA, w, h, false);
			if (maskTexture != null) maskTexture.close();
			maskTexture = new DynamicTexture(() -> "civcraft_territory_mask", maskImage);
			mc.getTextureManager().register(MASK_TEXTURE_ID, maskTexture);
			registered = true;
			builtW = w;
			builtH = h;
		}
		return maskTexture;
	}

	public static NativeImage image() { return maskImage; }
	public static boolean isReady() { return registered; }

	public static void close() {
		if (maskTexture != null) { maskTexture.close(); maskTexture = null; }
		if (maskImage != null)   { maskImage.close();   maskImage = null; }
		registered = false;
	}
}
