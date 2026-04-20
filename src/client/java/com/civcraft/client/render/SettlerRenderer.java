package com.civcraft.client.render;

import com.civcraft.entity.SettlerEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Settler leader is an invisible marker entity — it carries squad identity on
 * the server but has no model. We still need an {@link EntityRenderer} or MC
 * crashes when the dispatcher tries to look one up.
 *
 * The default EntityRenderer submit path renders nothing visual unless we
 * extend it, so inheriting the abstract base is enough.
 */
public class SettlerRenderer extends EntityRenderer<SettlerEntity, EntityRenderState> {
	public SettlerRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
