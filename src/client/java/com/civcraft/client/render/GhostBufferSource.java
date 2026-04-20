package com.civcraft.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Wraps an existing {@link MultiBufferSource} so that any block rendered into
 * it comes out on the translucent render type with a capped alpha. Used to
 * draw ghost-building previews using the blocks' real textures.
 */
public final class GhostBufferSource implements MultiBufferSource {
	private final MultiBufferSource delegate;
	private final int alpha;

	public GhostBufferSource(MultiBufferSource delegate, int alpha) {
		this.delegate = delegate;
		this.alpha = alpha;
	}

	@Override
	public VertexConsumer getBuffer(RenderType ignored) {
		return new AlphaConsumer(delegate.getBuffer(RenderTypes.translucentMovingBlock()), alpha);
	}

	private static final class AlphaConsumer implements VertexConsumer {
		private final VertexConsumer inner;
		private final int alpha;

		AlphaConsumer(VertexConsumer inner, int alpha) {
			this.inner = inner;
			this.alpha = alpha;
		}

		@Override public VertexConsumer addVertex(float x, float y, float z) { inner.addVertex(x, y, z); return this; }
		@Override public VertexConsumer setColor(int r, int g, int b, int a) { inner.setColor(r, g, b, alpha); return this; }
		@Override public VertexConsumer setColor(int argb) {
			int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
			inner.setColor(r, g, b, alpha);
			return this;
		}
		@Override public VertexConsumer setUv(float u, float v) { inner.setUv(u, v); return this; }
		@Override public VertexConsumer setUv1(int u, int v) { inner.setUv1(u, v); return this; }
		@Override public VertexConsumer setUv2(int u, int v) { inner.setUv2(u, v); return this; }
		@Override public VertexConsumer setNormal(float x, float y, float z) { inner.setNormal(x, y, z); return this; }
		@Override public VertexConsumer setLineWidth(float w) { inner.setLineWidth(w); return this; }
	}
}
