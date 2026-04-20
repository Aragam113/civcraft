package com.civcraft.client.render;

import com.civcraft.Civcraft;
import com.civcraft.client.CivcraftClient;
import com.civcraft.client.camera.TopDownMode;
import com.civcraft.client.selection.SelectionState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Draws in-world RTS feedback each frame:
 *   - a ring under every squad member (bright white when selected, dim grey otherwise)
 *   - a line from each moving unit to its current target (yellow)
 *
 * Everything uses {@link RenderType#lines()} which is cheap, unshaded and
 * renders on top of terrain exactly like vanilla debug outlines.
 */
public final class OverlayRenderer {
	private static final int RING_SEGMENTS = 24;
	private static final float RING_RADIUS = 0.55f;

	private OverlayRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(OverlayRenderer::render);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx) {
		if (!TopDownMode.active) return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;

		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack matrices = ctx.matrices();
		MultiBufferSource consumers = ctx.consumers();
		if (consumers == null) return;
		VertexConsumer buffer = consumers.getBuffer(RenderTypes.LINES);

		// Pass 1: per-unit rings, accumulate centroid of the moving selection.
		double sumX = 0, sumZ = 0;
		double sumTx = 0, sumTy = 0, sumTz = 0;
		int movingCount = 0;

		for (Entity e : level.entitiesForRendering()) {
			if (!CivcraftClient.isSquadMember(e)) continue;
			boolean selected = SelectionState.selected.contains(e.getUUID());

			int rgb = selected ? 0xFFFFFF : 0x888888;
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8)  & 0xFF;
			int b =  rgb        & 0xFF;
			int a = selected ? 255 : 120;

			double ex = e.getX() - cam.x;
			double ey = e.getY() + 0.02 - cam.y;
			double ez = e.getZ() - cam.z;

			drawRing(matrices, buffer, ex, ey, ez, RING_RADIUS, r, g, b, a);

			Vec3 target = Civcraft.MOVE_TARGETS.get(e.getUUID());
			if (selected && target != null) {
				sumX += e.getX();
				sumZ += e.getZ();
				sumTx += target.x;
				sumTy += target.y;
				sumTz += target.z;
				movingCount++;
			}
		}

		// Pass 2: one aggregate trajectory line + target marker for the squad.
		if (movingCount > 0) {
			double avgX = sumX / movingCount;
			double avgZ = sumZ / movingCount;
			double avgTx = sumTx / movingCount;
			double avgTy = sumTy / movingCount;
			double avgTz = sumTz / movingCount;

			// Origin Y: sample the ground under the average current position.
			int origY = level.getHeight(
					net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
					(int) Math.floor(avgX), (int) Math.floor(avgZ));

			double lx0 = avgX - cam.x;
			double ly0 = origY + 0.15 - cam.y;
			double lz0 = avgZ - cam.z;
			double lx1 = avgTx - cam.x;
			double ly1 = avgTy + 0.15 - cam.y;
			double lz1 = avgTz - cam.z;

			// Thick yellow core + a darker outer layer for depth.
			drawLine(matrices, buffer, lx0, ly0, lz0, lx1, ly1, lz1, 255, 215, 0, 255);
			drawLine(matrices, buffer, lx0, ly0 + 0.02, lz0, lx1, ly1 + 0.02, lz1, 120, 80, 0, 180);

			// Target marker — double ring (white over gold).
			drawRing(matrices, buffer, lx1, ly1, lz1, 0.9f, 255, 215, 0, 255);
			drawRing(matrices, buffer, lx1, ly1 + 0.05, lz1, 0.5f, 255, 255, 255, 255);
		}

		// Banners last: Font.drawInBatch internally flushes buffers; doing it
		// after all LINES drawing avoids a "Not building!" crash on the next
		// addVertex call.
		drawSquadBanners(ctx, mc, level, cam);
	}

	private static void drawSquadBanners(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx,
	                                     Minecraft mc,
	                                     ClientLevel level,
	                                     Vec3 cam) {
		// Group squad members by spatial proximity: each cluster gets one banner
		// at its centroid, roughly head-height above.
		java.util.List<Entity> squad = new java.util.ArrayList<>();
		for (Entity e : level.entitiesForRendering()) {
			if (CivcraftClient.isSquadMember(e)) squad.add(e);
		}
		if (squad.isEmpty()) return;

		boolean[] used = new boolean[squad.size()];
		for (int i = 0; i < squad.size(); i++) {
			if (used[i]) continue;
			Entity a = squad.get(i);
			double sumX = a.getX(), sumY = a.getY(), sumZ = a.getZ();
			int n = 1;
			used[i] = true;
			for (int j = i + 1; j < squad.size(); j++) {
				if (used[j]) continue;
				Entity b = squad.get(j);
				if (a.distanceTo(b) <= 10.0) {
					sumX += b.getX(); sumY += b.getY(); sumZ += b.getZ();
					n++;
					used[j] = true;
				}
			}
			double cx = sumX / n;
			double cy = sumY / n + 2.6;   // well above heads
			double cz = sumZ / n;
			drawBillboardBanner(ctx, mc, cam, cx, cy, cz, "⚔ Поселенцы");
		}
	}

	private static void drawBillboardBanner(
			net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx,
			Minecraft mc, Vec3 cam,
			double wx, double wy, double wz, String text) {
		PoseStack matrices = ctx.matrices();
		matrices.pushPose();
		matrices.translate(wx - cam.x, wy - cam.y, wz - cam.z);
		matrices.mulPose(mc.gameRenderer.getMainCamera().rotation());
		// Bigger than vanilla name-tag: stays readable even when zoomed out.
		matrices.scale(-0.05f, -0.05f, 0.05f);
		org.joml.Matrix4f pose = matrices.last().pose();
		net.minecraft.client.gui.Font font = mc.font;
		int w = font.width(text);
		int packedLight = 0x00F000F0;

		// Opaque dark plaque via drawInBatch's bgColor channel (see-through so
		// the banner reads even through terrain). Gold core text + 8-directional
		// outline for thickness.
		int bg   = 0xEE120B04;
		int gold = 0xFFD4AF37;
		int dark = 0xFF1A0E04;

		font.drawInBatch(text, -w / 2f, 0, gold, false, pose, ctx.consumers(),
				net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, bg, packedLight);
		font.drawInBatch8xOutline(
				net.minecraft.util.FormattedCharSequence.forward(
						text, net.minecraft.network.chat.Style.EMPTY),
				-w / 2f, 0, gold, dark, pose, ctx.consumers(), packedLight);

		matrices.popPose();

		if (ctx.consumers() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) {
			bs.endBatch();
		}
	}

	private static void drawRing(PoseStack matrices, VertexConsumer buffer,
	                             double cx, double cy, double cz, float radius,
	                             int r, int g, int b, int a) {
		PoseStack.Pose pose = matrices.last();
		for (int i = 0; i < RING_SEGMENTS; i++) {
			double a1 = Math.PI * 2 * i / RING_SEGMENTS;
			double a2 = Math.PI * 2 * (i + 1) / RING_SEGMENTS;
			float x1 = (float) (cx + Math.cos(a1) * radius);
			float z1 = (float) (cz + Math.sin(a1) * radius);
			float x2 = (float) (cx + Math.cos(a2) * radius);
			float z2 = (float) (cz + Math.sin(a2) * radius);
			buffer.addVertex(pose, x1, (float) cy, z1).setColor(r, g, b, a).setNormal(0f, 1f, 0f).setLineWidth(1.5f);
			buffer.addVertex(pose, x2, (float) cy, z2).setColor(r, g, b, a).setNormal(0f, 1f, 0f).setLineWidth(1.5f);
		}
	}

	private static void drawLine(PoseStack matrices, VertexConsumer buffer,
	                             double x1, double y1, double z1,
	                             double x2, double y2, double z2,
	                             int r, int g, int b, int a) {
		PoseStack.Pose pose = matrices.last();
		float nx = (float) (x2 - x1);
		float ny = (float) (y2 - y1);
		float nz = (float) (z2 - z1);
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-4f) return;
		nx /= len; ny /= len; nz /= len;
		buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(2f);
		buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(2f);
	}
}
