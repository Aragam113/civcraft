package com.civcraft.client.render;

import com.civcraft.client.lens.BlockMaskGrid;
import com.civcraft.client.lens.MaskAtlasPacker;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Backs {@code civcraft:mask} with a runtime {@link DynamicTexture}. The
 * {@code TextureInput} resolver inside {@code PostChain.createPass}
 * rewrites {@code civcraft:mask} → {@code civcraft:textures/effect/mask.png}
 * before calling {@code TextureManager.getTexture(...)}, so we register
 * our DynamicTexture under that full path. Registration happens lazily
 * on the first lens frame, which runs AFTER the initial resource reload
 * and BEFORE the first {@code getPostChain} call — so the post pass
 * captures a reference to our DynamicTexture instead of the file-loaded
 * SimpleTexture.
 */
public final class LensMaskTexture {
	/** Transformed resource path — matches what the PostChain resolver queries. */
	public static final Identifier TEXTURE_ID =
			Identifier.fromNamespaceAndPath("civcraft", "textures/effect/mask.png");

	private static DynamicTexture texture;
	private static final BlockMaskGrid grid = new BlockMaskGrid();

	private LensMaskTexture() {}

	public static BlockMaskGrid grid() {
		return grid;
	}

	public static NativeImage image() {
		return ensureTexture().getPixels();
	}

	/**
	 * Register our dynamic texture at the resolved path so subsequent
	 * getPostChain calls pick it up. Safe to call every frame — the
	 * underlying map put is idempotent.
	 */
	public static void ensureRegistered() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getTextureManager() == null) return;
		mc.getTextureManager().register(TEXTURE_ID, ensureTexture());
	}

	/**
	 * Push this frame's NativeImage contents into the GPU-side texture.
	 * Because the PostPass holds a direct reference to our DynamicTexture,
	 * subsequent sampling sees the fresh data.
	 */
	public static void uploadFromImage() {
		ensureTexture().upload();
	}

	private static DynamicTexture ensureTexture() {
		if (texture == null) {
			NativeImage img = new NativeImage(NativeImage.Format.RGBA,
					MaskAtlasPacker.ATLAS_W, MaskAtlasPacker.ATLAS_H, true);
			// Start fully black — MaskAtlasPacker.pack rewrites every pixel.
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					img.setPixelABGR(x, y, 0xFF000000);
				}
			}
			texture = new DynamicTexture(() -> "civcraft_mask_atlas", img);
		}
		return texture;
	}
}
