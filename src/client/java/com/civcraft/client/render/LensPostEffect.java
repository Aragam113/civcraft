package com.civcraft.client.render;

import com.civcraft.client.lens.LensState;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

/**
 * Color-keyed desaturation shader. No mask, no projection — the fragment
 * shader decides per-pixel whether a color is "kept" (matches an oak log /
 * plank / cobblestone palette) or turned grayscale. Useful both as a lens
 * effect and as a pipeline-health test.
 */
public final class LensPostEffect {
	private static final Identifier POST_CHAIN_ID =
			Identifier.fromNamespaceAndPath("civcraft", "civcraft_lens");

	private LensPostEffect() {}

	public static void register() {
		WorldRenderEvents.END_MAIN.register(ctx -> apply());
	}

	private static void apply() {
		if (LensState.mode == LensState.Mode.NONE) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) return;
		PostChain chain;
		try {
			chain = mc.getShaderManager().getPostChain(POST_CHAIN_ID,
					java.util.Set.of(PostChain.MAIN_TARGET_ID));
		} catch (Throwable t) {
			return;
		}
		if (chain == null) return;
		chain.process(mc.getMainRenderTarget(), GraphicsResourceAllocator.UNPOOLED);
	}
}
