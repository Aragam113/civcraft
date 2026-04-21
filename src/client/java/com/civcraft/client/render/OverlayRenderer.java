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

		// Gather squad clusters once — reused for both ring pass and trajectory.
		java.util.Map<Integer, java.util.List<Entity>> squads = new java.util.HashMap<>();
		for (Entity e : level.entitiesForRendering()) {
			if (!CivcraftClient.isSquadMember(e)) continue;
			int sid = CivcraftClient.squadId(e);
			if (sid == 0) sid = -Math.abs(e.getUUID().hashCode());
			squads.computeIfAbsent(sid, k -> new java.util.ArrayList<>()).add(e);
		}

		record RingSpec(double cx, double cy, double cz, float radius, int r, int g, int b, int a) {}
		java.util.List<RingSpec> ringSpecs = new java.util.ArrayList<>();
		double sumX = 0, sumZ = 0, sumTx = 0, sumTy = 0, sumTz = 0;
		int movingCount = 0;
		for (java.util.List<Entity> members : squads.values()) {
			boolean anySelected = false;
			double cx = 0, cy = 0, cz = 0;
			for (Entity e : members) {
				cx += e.getX(); cy += e.getY(); cz += e.getZ();
				if (SelectionState.selected.contains(e.getUUID())) anySelected = true;
			}
			cx /= members.size(); cy /= members.size(); cz /= members.size();
			double maxR = 0;
			for (Entity e : members) {
				double dx = e.getX() - cx, dz = e.getZ() - cz;
				double d = Math.hypot(dx, dz);
				if (d > maxR) maxR = d;
			}
			float radius = (float) Math.max(maxR + 0.7, RING_RADIUS);
			int rgb = anySelected ? 0xFFFFFF : 0x888888;
			ringSpecs.add(new RingSpec(cx, cy, cz, radius,
					(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
					anySelected ? 240 : 120));
			if (anySelected) {
				for (Entity e : members) {
					Vec3 target = Civcraft.MOVE_TARGETS.get(e.getUUID());
					if (target == null) continue;
					sumX += e.getX();
					sumZ += e.getZ();
					sumTx += target.x;
					sumTy += target.y;
					sumTz += target.z;
					movingCount++;
				}
			}
		}

		// LINES phase: only ghost gizmos (arrows + check/cross) — rings and
		// trajectories switch to filled-box so they can have real thickness.
		drawGhostLines(matrices, buffer, cam);

		// Filled pass: 1/3-block-thick squad rings + parabolic arc trajectory.
		VertexConsumer filled = consumers.getBuffer(RenderTypes.debugFilledBox());
		for (RingSpec s : ringSpecs) {
			drawRingThick(matrices, filled,
					s.cx() - cam.x, s.cy() + 0.02 - cam.y, s.cz() - cam.z,
					s.radius(), 0.33f,
					s.r(), s.g(), s.b(), s.a());
		}
		if (movingCount > 0) {
			double avgX  = sumX  / movingCount, avgZ  = sumZ  / movingCount;
			double avgTx = sumTx / movingCount, avgTy = sumTy / movingCount, avgTz = sumTz / movingCount;
			int origY = level.getHeight(
					net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
					(int) Math.floor(avgX), (int) Math.floor(avgZ));
			drawArcTrajectory(matrices, filled, cam, avgX, origY + 0.2, avgZ, avgTx, avgTy + 0.2, avgTz);
		}

		// Ghost building preview uses its own render-type wrap (invalidates
		// the buffers above; do it last).
		if (com.civcraft.client.building.GhostState.isActive()) {
			drawGhostBlocks(ctx, mc, cam);
		}

		// Banners last: Font.drawInBatch internally flushes buffers.
		drawSquadBanners(ctx, mc, level, cam);
	}

	private static void drawGhostLines(PoseStack matrices, VertexConsumer buffer, Vec3 cam) {
		if (!com.civcraft.client.building.GhostState.isActive()) return;
		boolean confirmed = com.civcraft.client.building.GhostState.confirmed;

		// Interactive gizmos — only before the build is committed.
		if (confirmed) return;
		for (com.civcraft.client.building.GhostState.Gizmo giz :
				com.civcraft.client.building.GhostState.Gizmo.values()) {
			Vec3 wp = com.civcraft.client.building.GhostState.gizmoPos(giz);
			double gx = wp.x - cam.x;
			double gy = wp.y - cam.y;
			double gz = wp.z - cam.z;
			switch (giz) {
				case ARROW_N -> drawArrow(matrices, buffer, gx, gy, gz,  0, -1, 212, 175, 55);
				case ARROW_S -> drawArrow(matrices, buffer, gx, gy, gz,  0,  1, 212, 175, 55);
				case ARROW_W -> drawArrow(matrices, buffer, gx, gy, gz, -1,  0, 212, 175, 55);
				case ARROW_E -> drawArrow(matrices, buffer, gx, gy, gz,  1,  0, 212, 175, 55);
			}
		}
	}

	private static void drawGhostBlocks(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx,
	                                    Minecraft mc, Vec3 cam) {
		var dispatcher = mc.getBlockRenderer();
		PoseStack matrices = ctx.matrices();
		MultiBufferSource consumers = ctx.consumers();
		if (consumers == null || mc.level == null) return;
		GhostBufferSource ghostBuf = new GhostBufferSource(consumers, 120);
		GhostBufferSource ringBuf  = new GhostBufferSource(consumers, 90);

		// Gray ring showing the allowed drag radius — only while unconfirmed.
		if (!com.civcraft.client.building.GhostState.confirmed) {
			drawDragRadiusRing(mc, dispatcher, matrices, ringBuf, cam);
		}

		double px = com.civcraft.client.building.GhostState.pos.getX();
		double py = com.civcraft.client.building.GhostState.pos.getY();
		double pz = com.civcraft.client.building.GhostState.pos.getZ();
		for (var gb : com.civcraft.client.building.GhostState.shape()) {
			matrices.pushPose();
			matrices.translate(
					px + gb.dx() - cam.x,
					py + gb.dy() - cam.y,
					pz + gb.dz() - cam.z);
			dispatcher.renderSingleBlock(gb.state(), matrices, ghostBuf,
					0x00F000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
			matrices.popPose();
		}
	}

	private static void drawDragRadiusRing(Minecraft mc,
	                                       net.minecraft.client.renderer.block.BlockRenderDispatcher dispatcher,
	                                       PoseStack matrices,
	                                       GhostBufferSource buf,
	                                       Vec3 cam) {
		int r = com.civcraft.Civcraft.GHOST_DRAG_RADIUS;
		int ox = com.civcraft.client.building.GhostState.originX;
		int oz = com.civcraft.client.building.GhostState.originZ;
		// Light gray, semi-transparent — sits on top of whatever block is at the
		// surface for each (x, z) column, following terrain height.
		var state = net.minecraft.world.level.block.Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
		int rSq = r * r;
		int innerSq = (r - 1) * (r - 1);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int d = dx * dx + dz * dz;
				if (d > rSq || d <= innerSq) continue;  // 1-block-thick ring only
				int wx = ox + dx;
				int wz = oz + dz;
				int gy = mc.level.getHeight(
						net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
				matrices.pushPose();
				matrices.translate(wx - cam.x, gy - cam.y, wz - cam.z);
				dispatcher.renderSingleBlock(state, matrices, buf,
						0x00F000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
				matrices.popPose();
			}
		}
	}

	private static void drawCubeEdges(PoseStack m, VertexConsumer buf,
	                                  double x0, double y0, double z0,
	                                  double x1, double y1, double z1,
	                                  int r, int g, int b, int a) {
		drawLine(m, buf, x0, y0, z0, x1, y0, z0, r, g, b, a);
		drawLine(m, buf, x1, y0, z0, x1, y0, z1, r, g, b, a);
		drawLine(m, buf, x1, y0, z1, x0, y0, z1, r, g, b, a);
		drawLine(m, buf, x0, y0, z1, x0, y0, z0, r, g, b, a);
		drawLine(m, buf, x0, y1, z0, x1, y1, z0, r, g, b, a);
		drawLine(m, buf, x1, y1, z0, x1, y1, z1, r, g, b, a);
		drawLine(m, buf, x1, y1, z1, x0, y1, z1, r, g, b, a);
		drawLine(m, buf, x0, y1, z1, x0, y1, z0, r, g, b, a);
		drawLine(m, buf, x0, y0, z0, x0, y1, z0, r, g, b, a);
		drawLine(m, buf, x1, y0, z0, x1, y1, z0, r, g, b, a);
		drawLine(m, buf, x1, y0, z1, x1, y1, z1, r, g, b, a);
		drawLine(m, buf, x0, y0, z1, x0, y1, z1, r, g, b, a);
	}

	/** Triangle arrowhead pointing outward along (dx, dz) on a flat XZ plane. */
	private static void drawArrow(PoseStack m, VertexConsumer buf,
	                              double tipX, double tipY, double tipZ,
	                              double dx, double dz, int r, int g, int b) {
		// The given (tipX, tipY, tipZ) is the anchor; place tip 0.8 blocks out.
		double tx = tipX + dx * 0.8;
		double tz = tipZ + dz * 0.8;
		double bx = tipX - dx * 0.4;
		double bz = tipZ - dz * 0.4;
		double perpX = dz;
		double perpZ = -dx;
		double lx = bx + perpX * 0.5;
		double lz = bz + perpZ * 0.5;
		double rx = bx - perpX * 0.5;
		double rz = bz - perpZ * 0.5;
		// Triangle outline (tip → left, tip → right, left → right)
		drawLine(m, buf, tx, tipY, tz, lx, tipY, lz, r, g, b, 255);
		drawLine(m, buf, tx, tipY, tz, rx, tipY, rz, r, g, b, 255);
		drawLine(m, buf, lx, tipY, lz, rx, tipY, rz, r, g, b, 255);
		// Stem: small line from center to tip for extra visibility.
		drawLine(m, buf, bx, tipY, bz, tx, tipY, tz, r, g, b, 255);
	}

	/** Two-stroke V-shape drawn on a flat XZ plane. */
	private static void drawCheck(PoseStack m, VertexConsumer buf,
	                              double cx, double cy, double cz, int r, int g, int b) {
		// ✓ in XZ plane: a short left leg, then a longer right leg going up-right.
		drawLine(m, buf, cx - 0.4, cy, cz - 0.1, cx - 0.1, cy, cz + 0.3, r, g, b, 255);
		drawLine(m, buf, cx - 0.1, cy, cz + 0.3, cx + 0.5, cy, cz - 0.4, r, g, b, 255);
		// Thicker: double-draw with a slight vertical offset.
		drawLine(m, buf, cx - 0.4, cy + 0.05, cz - 0.1, cx - 0.1, cy + 0.05, cz + 0.3, r, g, b, 255);
		drawLine(m, buf, cx - 0.1, cy + 0.05, cz + 0.3, cx + 0.5, cy + 0.05, cz - 0.4, r, g, b, 255);
	}

	/** X drawn on a flat XZ plane. */
	private static void drawCross(PoseStack m, VertexConsumer buf,
	                              double cx, double cy, double cz, int r, int g, int b) {
		drawLine(m, buf, cx - 0.4, cy, cz - 0.4, cx + 0.4, cy, cz + 0.4, r, g, b, 255);
		drawLine(m, buf, cx - 0.4, cy, cz + 0.4, cx + 0.4, cy, cz - 0.4, r, g, b, 255);
		drawLine(m, buf, cx - 0.4, cy + 0.05, cz - 0.4, cx + 0.4, cy + 0.05, cz + 0.4, r, g, b, 255);
		drawLine(m, buf, cx - 0.4, cy + 0.05, cz + 0.4, cx + 0.4, cy + 0.05, cz - 0.4, r, g, b, 255);
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

	/** Filled annulus of given outer radius and band thickness, lying flat on XZ. */
	private static void drawRingThick(PoseStack m, VertexConsumer buf,
	                                  double cx, double cy, double cz,
	                                  double radius, double thickness,
	                                  int r, int g, int b, int a) {
		double inner = Math.max(0, radius - thickness / 2);
		double outer = radius + thickness / 2;
		int segs = RING_SEGMENTS * 2;  // smoother curve for thick band
		PoseStack.Pose pose = m.last();
		for (int i = 0; i < segs; i++) {
			double a1 = Math.PI * 2 * i / segs;
			double a2 = Math.PI * 2 * (i + 1) / segs;
			double c1 = Math.cos(a1), s1 = Math.sin(a1);
			double c2 = Math.cos(a2), s2 = Math.sin(a2);
			float y = (float) cy;
			buf.addVertex(pose, (float)(cx + inner * c1), y, (float)(cz + inner * s1)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + outer * c1), y, (float)(cz + outer * s1)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + outer * c2), y, (float)(cz + outer * s2)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + inner * c2), y, (float)(cz + inner * s2)).setColor(r, g, b, a);
		}
	}

	/** Parabolic arc of white cubes from (x0,y0,z0) to (x1,y1,z1) with a
	 *  chunky arrowhead block at the destination. */
	private static void drawArcTrajectory(PoseStack m, VertexConsumer buf, Vec3 cam,
	                                      double x0, double y0, double z0,
	                                      double x1, double y1, double z1) {
		double dist = Math.hypot(x1 - x0, z1 - z0);
		double arcHeight = Math.max(2.5, dist * 0.35);
		int steps = 24;
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			double x = x0 + (x1 - x0) * t;
			double z = z0 + (z1 - z0) * t;
			double yBase = y0 + (y1 - y0) * t;
			double y = yBase + 4 * arcHeight * t * (1 - t);
			double half = (0.30 + 0.35 * t) * 0.5;  // thicker near the target
			drawFilledCube(m, buf,
					x - cam.x - half, y - cam.y - half, z - cam.z - half,
					x - cam.x + half, y - cam.y + half, z - cam.z + half,
					245, 245, 245, 235);
		}
		// Arrowhead: a 1-block cube elevated at the destination, sitting on top
		// of the arc's last sample.
		double ah = 0.55;
		double atY = y1 + 0.9;
		drawFilledCube(m, buf,
				x1 - cam.x - ah, atY - cam.y,          z1 - cam.z - ah,
				x1 - cam.x + ah, atY - cam.y + ah * 2, z1 - cam.z + ah,
				255, 255, 255, 255);
	}

	private static void drawFilledCube(PoseStack m, VertexConsumer buf,
	                                   double x0, double y0, double z0,
	                                   double x1, double y1, double z1,
	                                   int r, int g, int b, int a) {
		PoseStack.Pose p = m.last();
		addQuad(p, buf, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r, g, b, a);
		addQuad(p, buf, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
		addQuad(p, buf, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
		addQuad(p, buf, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);
		addQuad(p, buf, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, a);
		addQuad(p, buf, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
	}

	private static void addQuad(PoseStack.Pose p, VertexConsumer buf,
	                            double ax, double ay, double az,
	                            double bx, double by, double bz,
	                            double cx, double cy, double cz,
	                            double dx, double dy, double dz,
	                            int r, int g, int b, int a) {
		buf.addVertex(p, (float) ax, (float) ay, (float) az).setColor(r, g, b, a);
		buf.addVertex(p, (float) bx, (float) by, (float) bz).setColor(r, g, b, a);
		buf.addVertex(p, (float) cx, (float) cy, (float) cz).setColor(r, g, b, a);
		buf.addVertex(p, (float) dx, (float) dy, (float) dz).setColor(r, g, b, a);
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
