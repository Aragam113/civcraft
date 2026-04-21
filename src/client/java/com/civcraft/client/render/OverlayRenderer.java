package com.civcraft.client.render;

import com.civcraft.Civcraft;
import com.civcraft.client.CivcraftClient;
import com.civcraft.client.camera.TopDownMode;
import com.civcraft.client.selection.SelectionState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

	/** Client-side snapshot of each active move order so the trajectory arc
	 *  stays pinned between the unit's position AT ORDER TIME and the fixed
	 *  target — it won't re-curve every frame as the unit walks along it. */
	private record MoveSnap(double sx, double sy, double sz,
	                        double tx, double ty, double tz) {}
	private static final java.util.Map<UUID, MoveSnap> SNAPS = new java.util.HashMap<>();

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
					UUID id = e.getUUID();
					Vec3 target = Civcraft.MOVE_TARGETS.get(id);
					if (target == null) {
						SNAPS.remove(id);
						continue;
					}
					MoveSnap snap = SNAPS.get(id);
					// Re-snapshot when the order changes (different target).
					if (snap == null
							|| Math.abs(snap.tx() - target.x) > 0.5
							|| Math.abs(snap.tz() - target.z) > 0.5) {
						snap = new MoveSnap(
								e.getX(), e.getY(), e.getZ(),
								target.x, target.y, target.z);
						SNAPS.put(id, snap);
					}
					sumX  += snap.sx();
					sumZ  += snap.sz();
					sumTx += snap.tx();
					sumTy += snap.ty();
					sumTz += snap.tz();
					movingCount++;
				}
			}
		}
		// Drop snapshots for units that have finished their move.
		SNAPS.keySet().removeIf(id -> !Civcraft.MOVE_TARGETS.containsKey(id));

		// LINES phase: only ghost gizmos (arrows + check/cross) — rings and
		// trajectories switch to filled-box so they can have real thickness.
		drawGhostLines(matrices, buffer, cam);

		// Filled pass: 1/3-block-thick squad rings + parabolic arc trajectory.
		VertexConsumer filled = consumers.getBuffer(RenderTypes.debugFilledBox());
		for (RingSpec s : ringSpecs) {
			drawRingThick(matrices, filled,
					s.cx() - cam.x, s.cy() + 0.12 - cam.y, s.cz() - cam.z,
					s.radius(), 0.33f,
					s.r(), s.g(), s.b(), s.a());
		}
		if (movingCount > 0) {
			double avgX  = sumX  / movingCount, avgZ  = sumZ  / movingCount;
			double avgTx = sumTx / movingCount, avgTy = sumTy / movingCount, avgTz = sumTz / movingCount;
			// Ground-snap endpoints so the arc starts and ends on the surface
			// at their respective columns (terrain may differ from anchorY).
			int origY  = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
					(int) Math.floor(avgX),  (int) Math.floor(avgZ));
			int destY  = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
					(int) Math.floor(avgTx), (int) Math.floor(avgTz));
			drawArcTrajectory(matrices, filled, cam, avgX, origY + 0.2, avgZ, avgTx, destY + 0.2, avgTz);
		}

		// Lens overlay: tint tiles outside the player's territory.
		if (com.civcraft.client.lens.LensState.mode != com.civcraft.client.lens.LensState.Mode.NONE) {
			drawLensOverlay(matrices, filled, mc, level, cam);
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

	// Cached list of town-hall block positions near the camera; refreshed a few
	// times per second since scanning every frame is wasteful.
	private static java.util.List<BlockPos> cachedHalls = java.util.List.of();
	private static long lastHallScan = 0;
	private static double lastScanX = 0, lastScanZ = 0;

	private static java.util.List<BlockPos> nearbyTownHalls(Minecraft mc) {
		long now = System.currentTimeMillis();
		double dx = com.civcraft.client.camera.TopDownMode.anchorX - lastScanX;
		double dz = com.civcraft.client.camera.TopDownMode.anchorZ - lastScanZ;
		if (now - lastHallScan < 800 && dx * dx + dz * dz < 16) return cachedHalls;
		if (mc.level == null) return cachedHalls;
		int scanR = 96;
		int cx = (int) com.civcraft.client.camera.TopDownMode.anchorX;
		int cz = (int) com.civcraft.client.camera.TopDownMode.anchorZ;
		java.util.List<BlockPos> list = new java.util.ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = cx - scanR; x <= cx + scanR; x += 2) {
			for (int z = cz - scanR; z <= cz + scanR; z += 2) {
				int gy = mc.level.getHeight(
						net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
				for (int dy = -2; dy <= 10; dy++) {
					m.set(x, gy + dy, z);
					if (mc.level.getBlockState(m).getBlock()
							== com.civcraft.registry.ModBlocks.TOWN_HALL) {
						list.add(m.immutable());
						break;
					}
				}
			}
		}
		cachedHalls = list;
		lastHallScan = now;
		lastScanX = com.civcraft.client.camera.TopDownMode.anchorX;
		lastScanZ = com.civcraft.client.camera.TopDownMode.anchorZ;
		return list;
	}

	private static void drawLensOverlay(PoseStack matrices, VertexConsumer filled,
	                                    Minecraft mc, net.minecraft.client.multiplayer.ClientLevel level,
	                                    Vec3 cam) {
		int radius = com.civcraft.client.lens.LensState.mode.radius();
		if (radius <= 0) return;
		java.util.List<BlockPos> halls = nearbyTownHalls(mc);
		int viewR = 64;  // tiles shown relative to camera anchor
		int cx = (int) com.civcraft.client.camera.TopDownMode.anchorX;
		int cz = (int) com.civcraft.client.camera.TopDownMode.anchorZ;
		int rSq = radius * radius;
		PoseStack.Pose pose = matrices.last();
		for (int x = cx - viewR; x <= cx + viewR; x++) {
			for (int z = cz - viewR; z <= cz + viewR; z++) {
				boolean inside = false;
				for (BlockPos h : halls) {
					int dx = x - h.getX();
					int dz = z - h.getZ();
					if (dx * dx + dz * dz <= rSq) { inside = true; break; }
				}
				if (inside) continue;
				int gy = level.getHeight(
						net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
				float y = (float) (gy + 0.01 - cam.y);
				float x0 = (float) (x - cam.x);
				float z0 = (float) (z - cam.z);
				// Single dark-gray translucent quad over the column.
				addQuad(pose, filled,
						x0,     y, z0,
						x0,     y, z0 + 1,
						x0 + 1, y, z0 + 1,
						x0 + 1, y, z0,
						40, 40, 48, 160);
			}
		}
	}

	/** Flat annulus (ring band) on the XZ plane, {@code thickness} blocks wide.
	 *  Double-sided so it stays visible regardless of face-culling. */
	private static void drawRingThick(PoseStack m, VertexConsumer buf,
	                                  double cx, double cy, double cz,
	                                  double radius, double thickness,
	                                  int r, int g, int b, int a) {
		double inner = Math.max(0, radius - thickness / 2);
		double outer = radius + thickness / 2;
		int segs = 64;
		PoseStack.Pose pose = m.last();
		float y = (float) cy;
		for (int i = 0; i < segs; i++) {
			double a1 = Math.PI * 2 * i / segs;
			double a2 = Math.PI * 2 * (i + 1) / segs;
			float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
			// Top face (normal +Y)
			buf.addVertex(pose, (float)(cx + inner * c1), y, (float)(cz + inner * s1)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + inner * c2), y, (float)(cz + inner * s2)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + outer * c2), y, (float)(cz + outer * s2)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + outer * c1), y, (float)(cz + outer * s1)).setColor(r, g, b, a);
			// Bottom face (normal -Y) — opposite winding so it shows through.
			buf.addVertex(pose, (float)(cx + inner * c1), y, (float)(cz + inner * s1)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + outer * c1), y, (float)(cz + outer * s1)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + outer * c2), y, (float)(cz + outer * s2)).setColor(r, g, b, a);
			buf.addVertex(pose, (float)(cx + inner * c2), y, (float)(cz + inner * s2)).setColor(r, g, b, a);
		}
	}

	/** 3D ribbon arrow bent into a parabolic arc. Horizontal ribbon tapers
	 *  from thin at origin to wide near destination, followed by a flat
	 *  triangular arrowhead pointing along the tangent. White + fully opaque. */
	private static void drawArcTrajectory(PoseStack m, VertexConsumer buf, Vec3 cam,
	                                      double x0, double y0, double z0,
	                                      double x1, double y1, double z1) {
		double dist = Math.hypot(x1 - x0, z1 - z0);
		if (dist < 0.1) return;
		double arcHeight = Math.max(2.5, dist * 0.35);
		int steps = 32;
		// Sample points along the parabola.
		double[] sx = new double[steps + 1];
		double[] sy = new double[steps + 1];
		double[] sz = new double[steps + 1];
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			sx[i] = x0 + (x1 - x0) * t;
			sz[i] = z0 + (z1 - z0) * t;
			sy[i] = y0 + (y1 - y0) * t + 4 * arcHeight * t * (1 - t);
		}

		PoseStack.Pose pose = m.last();
		int r = 250, g = 250, b = 250, a = 240;
		// Leave the last segment for the arrowhead so it doesn't overlap.
		int ribbonSteps = steps - 3;

		for (int i = 0; i < ribbonSteps; i++) {
			double t0 = i / (double) steps;
			double t1 = (i + 1) / (double) steps;
			// Width tapers 0.18 → 0.45 blocks.
			double w0 = 0.18 + 0.27 * t0;
			double w1 = 0.18 + 0.27 * t1;
			// Perpendicular in XZ to the local tangent.
			double fx = sx[i + 1] - sx[i];
			double fz = sz[i + 1] - sz[i];
			double fl = Math.hypot(fx, fz);
			if (fl < 1e-5) continue;
			double rx =  fz / fl, rz = -fx / fl;
			emitRibbonQuad(pose, buf,
					sx[i]     + rx * w0, sy[i],     sz[i]     + rz * w0,
					sx[i]     - rx * w0, sy[i],     sz[i]     - rz * w0,
					sx[i + 1] - rx * w1, sy[i + 1], sz[i + 1] - rz * w1,
					sx[i + 1] + rx * w1, sy[i + 1], sz[i + 1] + rz * w1,
					cam, r, g, b, a);
		}

		// Arrowhead: flat triangle pointing along the final tangent, wider than
		// the ribbon tip for a clear "arrow" silhouette.
		int n = steps;
		double fx = sx[n] - sx[n - 3];
		double fz = sz[n] - sz[n - 3];
		double fl = Math.hypot(fx, fz);
		if (fl < 1e-5) return;
		double fxN = fx / fl, fzN = fz / fl;
		double rxN = fzN, rzN = -fxN;
		double headLen  = 1.4;
		double headHalf = 0.65;
		double tipX = sx[n],          tipY = sy[n],          tipZ = sz[n];
		double baseX = tipX - fxN * headLen;
		double baseZ = tipZ - fzN * headLen;
		double baseY = sy[n - 3];
		double lX = baseX + rxN * headHalf, lZ = baseZ + rzN * headHalf;
		double rX = baseX - rxN * headHalf, rZ = baseZ - rzN * headHalf;
		// Thin vertical extrusion so the arrowhead has some 3D presence.
		double lift = 0.08;
		emitArrowTri(pose, buf, cam,
				tipX, tipY + lift, tipZ,
				lX,   baseY + lift, lZ,
				rX,   baseY + lift, rZ,
				255, 255, 255, 255);
		// Underside, wound the other way so the tri is visible from below too.
		emitArrowTri(pose, buf, cam,
				tipX, tipY - lift, tipZ,
				rX,   baseY - lift, rZ,
				lX,   baseY - lift, lZ,
				255, 255, 255, 255);
	}

	private static void emitRibbonQuad(PoseStack.Pose p, VertexConsumer buf,
	                                   double ax, double ay, double az,
	                                   double bx, double by, double bz,
	                                   double cx, double cy, double cz,
	                                   double dx, double dy, double dz,
	                                   Vec3 cam, int r, int g, int b, int a) {
		addQuad(p, buf,
				ax - cam.x, ay - cam.y, az - cam.z,
				bx - cam.x, by - cam.y, bz - cam.z,
				cx - cam.x, cy - cam.y, cz - cam.z,
				dx - cam.x, dy - cam.y, dz - cam.z,
				r, g, b, a);
		// Double-sided so the ribbon shows from above and below.
		addQuad(p, buf,
				ax - cam.x, ay - cam.y, az - cam.z,
				dx - cam.x, dy - cam.y, dz - cam.z,
				cx - cam.x, cy - cam.y, cz - cam.z,
				bx - cam.x, by - cam.y, bz - cam.z,
				r, g, b, a);
	}

	private static void emitArrowTri(PoseStack.Pose p, VertexConsumer buf, Vec3 cam,
	                                 double tipX, double tipY, double tipZ,
	                                 double lX, double lY, double lZ,
	                                 double rX, double rY, double rZ,
	                                 int r, int g, int b, int a) {
		// Emit as a degenerate quad (tip doubled) so we stay on the quad-based
		// debugFilledBox pipeline.
		float txc = (float)(tipX - cam.x), tyc = (float)(tipY - cam.y), tzc = (float)(tipZ - cam.z);
		float lxc = (float)(lX - cam.x),   lyc = (float)(lY - cam.y),   lzc = (float)(lZ - cam.z);
		float rxc = (float)(rX - cam.x),   ryc = (float)(rY - cam.y),   rzc = (float)(rZ - cam.z);
		buf.addVertex(p, txc, tyc, tzc).setColor(r, g, b, a);
		buf.addVertex(p, lxc, lyc, lzc).setColor(r, g, b, a);
		buf.addVertex(p, rxc, ryc, rzc).setColor(r, g, b, a);
		buf.addVertex(p, txc, tyc, tzc).setColor(r, g, b, a);
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
