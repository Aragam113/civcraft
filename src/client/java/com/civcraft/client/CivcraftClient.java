package com.civcraft.client;

import com.civcraft.Civcraft;
import com.civcraft.client.camera.CameraMath;
import com.civcraft.client.camera.TopDownMode;
import com.civcraft.client.hud.CivcraftHud;
import com.civcraft.client.render.SettlerRenderer;
import com.civcraft.client.selection.SelectionState;
import com.civcraft.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class CivcraftClient implements ClientModInitializer {
	private static KeyMapping toggleCameraKey;
	private static KeyMapping perkKey;
	private static KeyMapping perk2Key;

	private boolean prevLmb = false;
	private boolean prevRmb = false;
	private boolean prevMmb = false;
	private double mmbStartX, mmbStartY;
	private double mmbStartAnchorX, mmbStartAnchorZ;
	private float mmbStartYaw;
	private boolean mmbWasRotate;
	private int ticksSinceJoin = -1;  // -1 = not joined yet

	@Override
	public void onInitializeClient() {
		Civcraft.LOGGER.info("[CivCraft] Initializing client-side systems");

		toggleCameraKey = bind("key.civcraft.toggle_camera", GLFW.GLFW_KEY_C);
		perkKey         = bind("key.civcraft.perk",         GLFW.GLFW_KEY_Q);
		perk2Key        = bind("key.civcraft.perk2",        GLFW.GLFW_KEY_E);

		ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
		CivcraftHud.register();
		com.civcraft.client.render.OverlayRenderer.register();
		EntityRendererRegistry.register(ModEntities.SETTLER, SettlerRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(
				com.civcraft.network.ResourceSyncPayload.ID, (payload, context) -> {
					com.civcraft.client.resource.ResourceState.food       = payload.food();
					com.civcraft.client.resource.ResourceState.production = payload.production();
					com.civcraft.client.resource.ResourceState.gold       = payload.gold();
					com.civcraft.client.resource.ResourceState.science    = payload.science();
					com.civcraft.client.resource.ResourceState.culture    = payload.culture();
				});

		ClientPlayNetworking.registerGlobalReceiver(
				com.civcraft.network.GhostStatePayload.ID, (payload, context) -> {
					if (payload.kind() == com.civcraft.network.GhostStatePayload.KIND_NONE) {
						com.civcraft.client.building.GhostState.clear();
					} else {
						com.civcraft.client.building.GhostState.apply(
								payload.kind(), payload.pos(),
								payload.originX(), payload.originZ(),
								payload.progress(), payload.target(), payload.confirmed());
					}
				});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			ticksSinceJoin = 0;
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			// Drop client-only state so the next tick can't fire a packet into
			// a dead connection (causes "Cannot send packets when not in game").
			com.civcraft.client.building.GhostState.clear();
			SelectionState.clearAll();
			TopDownMode.active = false;
			ticksSinceJoin = -1;
		});
	}

	private static KeyMapping bind(String key, int glfwKey) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(
				key, InputConstants.Type.KEYSYM, glfwKey, KeyMapping.Category.MISC));
	}

	private void clientTick(Minecraft client) {
		// During world-exit the connection tears down before DISCONNECT fires;
		// skip all RTS logic if we aren't actively in a level.
		if (client.level == null || client.player == null || client.player.connection == null) {
			return;
		}
		if (ticksSinceJoin >= 0) {
			ticksSinceJoin++;
			// Auto-enter RTS once the world has had 2s to load. Only auto-toggle
			// if the user hasn't already flipped it manually.
			if (ticksSinceJoin == 40 && !TopDownMode.active && client.player != null) {
				TopDownMode.toggle();
				onIsoToggled(client);
			}
		}
		while (toggleCameraKey.consumeClick()) {
			if (client.player == null) continue;
			TopDownMode.toggle();
			onIsoToggled(client);
		}
		if (TopDownMode.active) {
			// Snapshot prev state so the camera mixin can interpolate this tick's
			// motion into smooth per-frame movement between input ticks.
			TopDownMode.snapshot();
			panCameraByKeys(client);
			pollMiddleMouse(client);
			holdCursorFree(client);
			pollMouseButtons(client);
			syncGlowWithSelection(client);
			handlePerkKey(client);
			followAnchorWithPlayer(client);
		}
	}

	/**
	 * Keep the server-side player hovering around the camera anchor so entity
	 * trackers and chunk loading follow what the user is actually looking at.
	 * Uses absMoveTo to preserve rotation, and only fires when the player is
	 * fully spawned and the target chunk is already loaded — both guards avoid
	 * the null-camera crash seen when the world is still initializing.
	 */
	private void followAnchorWithPlayer(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || !player.isAlive()) return;
		if (client.level == null) return;
		if (player.connection == null) return;
		int cx = (int) Math.floor(TopDownMode.anchorX);
		int cz = (int) Math.floor(TopDownMode.anchorZ);
		if (!client.level.hasChunk(cx >> 4, cz >> 4)) return;
		double dx = TopDownMode.anchorX - player.getX();
		double dz = TopDownMode.anchorZ - player.getZ();
		if (dx * dx + dz * dz < 1.0) return;
		player.snapTo(TopDownMode.anchorX, player.getY(), TopDownMode.anchorZ,
				player.getYRot(), player.getXRot());
	}

	private void openGameMenu(Minecraft client) {
		client.setScreen(new com.civcraft.client.gui.CivcraftWikiScreen());
	}

	private void handlePerkKey(Minecraft client) {
		while (perkKey.consumeClick()) {
			if (com.civcraft.client.building.GhostState.isActive()
					&& !com.civcraft.client.building.GhostState.confirmed) {
				cancelGhostWithRefund();
				continue;
			}
			firePerk(client, 0);
		}
		while (perk2Key.consumeClick()) {
			firePerk(client, 1);
		}
	}

	private void firePerk(Minecraft client, int slot) {
		if (com.civcraft.client.building.GhostState.isActive()) {
			return;  // already have a ghost; user must confirm/cancel first
		}
		if (SelectionState.kind == SelectionState.Kind.BUILDER_SQUAD) {
			byte kind = switch (slot) {
				case 0  -> com.civcraft.network.SpawnGhostPayload.KIND_SMITHY;
				case 1  -> com.civcraft.network.SpawnGhostPayload.KIND_SAWMILL;
				case 2  -> com.civcraft.network.SpawnGhostPayload.KIND_STOREHOUSE;
				case 3  -> com.civcraft.network.SpawnGhostPayload.KIND_QUARRY;
				default -> com.civcraft.network.SpawnGhostPayload.KIND_SMITHY;
			};
			if (SelectionState.selected.isEmpty()) {
				if (client.player != null) {
					client.player.displayClientMessage(
							Component.literal("§cНет строителей — отряд исчерпан"), true);
				}
				return;
			}
			sendSpawnGhost(client, kind, SelectionState.selected);
			return;
		}
		switch (SelectionState.kind) {
			case SQUAD -> {
				if (!com.civcraft.client.resource.ResourceState.canAffordTownHall()) {
					if (client.player != null) {
						client.player.displayClientMessage(Component.literal(
								String.format("§cНе хватает ресурсов для ратуши (нужно: %d еды, %d производства)",
										com.civcraft.client.resource.ResourceState.TOWN_HALL_FOOD,
										com.civcraft.client.resource.ResourceState.TOWN_HALL_PRODUCTION)), true);
					}
					return;
				}
				com.civcraft.client.resource.ResourceState.deductTownHall();
				sendSpawnGhost(client, com.civcraft.network.SpawnGhostPayload.KIND_TOWNHALL,
						SelectionState.selected);
				SelectionState.clearAll();
			}
			case BUILDING -> {
				if (SelectionState.selectedBuilding == null) return;
				net.minecraft.core.BlockPos spire = SelectionState.selectedBuilding;
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.civcraft.network.SpawnSettlersPayload(spire));
				// Expected squad centroid — centered under the spire, SPAWN_SOUTH_OFFSET blocks south.
				double cx = spire.getX() + 0.5;
				double cz = spire.getZ() - com.civcraft.Civcraft.SPAWN_SOUTH_OFFSET + 0.5;
				TopDownMode.anchorX = cx;
				TopDownMode.anchorZ = cz;
				if (client.level != null) {
					TopDownMode.anchorY = client.level.getHeight(
							net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
							(int) Math.floor(cx), (int) Math.floor(cz));
				}
				if (client.player != null) {
					client.player.displayClientMessage(Component.literal(String.format(
							"§6Отряд у (%.0f, %.0f, %.0f)", cx, TopDownMode.anchorY, cz)), true);
				}
			}
			default -> {
				if (client.player != null) {
					client.player.displayClientMessage(
							Component.literal("§7Выделите отряд или здание."), true);
				}
			}
		}
	}

	private void sendSpawnGhost(Minecraft client, byte kind, java.util.Set<UUID> units) {
		if (client.level == null) return;
		// Initial ghost pos: centroid of the contributing squad.
		double sx = 0, sz = 0;
		int n = 0;
		for (UUID u : units) {
			Entity e = null;
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity.getUUID().equals(u)) { e = entity; break; }
			}
			if (e != null) { sx += e.getX(); sz += e.getZ(); n++; }
		}
		int px, pz;
		if (n > 0) {
			px = (int) Math.floor(sx / n);
			pz = (int) Math.floor(sz / n);
		} else {
			px = (int) TopDownMode.anchorX;
			pz = (int) TopDownMode.anchorZ;
		}
		int py = client.level.getHeight(
				net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, px, pz);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.civcraft.network.SpawnGhostPayload(kind, px, py, pz,
						new java.util.ArrayList<>(units)));
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(
					"§6Призрак появился — удерживай ЛКМ, чтобы перетащить, ПКМ чтобы подтвердить, Q — отменить"), true);
		}
	}

	private void pollMouseButtons(Minecraft client) {
		long window = client.getWindow().handle();
		boolean lmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean rmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		double mx = client.mouseHandler.xpos();
		double my = client.mouseHandler.ypos();

		// Active unconfirmed ghost: HUD buttons > world gizmos > drag, RMB=confirm.
		if (com.civcraft.client.building.GhostState.isActive()
				&& !com.civcraft.client.building.GhostState.confirmed) {
			if (lmb && !prevLmb) {
				if (com.civcraft.client.hud.CivcraftHud.isMouseOverPlanet(client)) {
					openGameMenu(client);
					prevLmb = true;
					return;
				}
				int hudBtn = com.civcraft.client.hud.CivcraftHud.mouseGhostButton(client);
				if (hudBtn == 0) {
					net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
							new com.civcraft.network.ConfirmGhostPayload());
					prevLmb = true; prevRmb = rmb;
					return;
				}
				if (hudBtn == 1) {
					cancelGhostWithRefund();
					prevLmb = true; prevRmb = rmb;
					return;
				}
				var giz = detectGizmoUnderMouse(client, mx, my);
				if (giz != null) {
					handleGizmoClick(giz);
					prevLmb = true;
					prevRmb = rmb;
					return;
				}
				com.civcraft.client.building.GhostState.dragging = true;
			}
			if (lmb && com.civcraft.client.building.GhostState.dragging) {
				sendGhostDrag(client, mx, my);
			}
			if (!lmb && prevLmb) {
				com.civcraft.client.building.GhostState.dragging = false;
			}
			if (rmb && !prevRmb) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.civcraft.network.ConfirmGhostPayload());
			}
			prevLmb = lmb;
			prevRmb = rmb;
			return;
		}

		if (lmb && !prevLmb) {
			if (com.civcraft.client.hud.CivcraftHud.isMouseOverPlanet(client)) {
				openGameMenu(client);
				prevLmb = true;
				return;
			}
			if (SelectionState.kind != SelectionState.Kind.NONE) {
				int slot = com.civcraft.client.hud.CivcraftHud.mousePerkSlot(client);
				if (slot >= 0) {
					firePerk(client, slot);
					prevLmb = true;
					return;
				}
			}
			SelectionState.begin(mx, my);
		} else if (!lmb && prevLmb) {
			SelectionState.end();
			resolveSelection(client);
		} else if (lmb) {
			SelectionState.update(mx, my);
		}
		if (rmb && !prevRmb) {
			issueMoveOrder(client);
		}

		prevLmb = lmb;
		prevRmb = rmb;
	}

	private static final double GIZMO_PIXEL_RADIUS = 28.0;

	private com.civcraft.client.building.GhostState.Gizmo detectGizmoUnderMouse(
			Minecraft client, double mx, double my) {
		com.civcraft.client.building.GhostState.Gizmo best = null;
		double bestD2 = GIZMO_PIXEL_RADIUS * GIZMO_PIXEL_RADIUS;
		for (var giz : com.civcraft.client.building.GhostState.Gizmo.values()) {
			var wp = com.civcraft.client.building.GhostState.gizmoPos(giz);
			float[] s = CameraMath.worldToScreen(client, wp.x, wp.y, wp.z);
			if (s == null) continue;
			double dx = s[0] - mx, dy = s[1] - my;
			double d2 = dx * dx + dy * dy;
			if (d2 < bestD2) { bestD2 = d2; best = giz; }
		}
		return best;
	}

	private void handleGizmoClick(com.civcraft.client.building.GhostState.Gizmo giz) {
		var pos = com.civcraft.client.building.GhostState.pos;
		switch (giz) {
			case ARROW_N -> sendGhostPos(pos.getX(),     pos.getY(), pos.getZ() - 1);
			case ARROW_S -> sendGhostPos(pos.getX(),     pos.getY(), pos.getZ() + 1);
			case ARROW_W -> sendGhostPos(pos.getX() - 1, pos.getY(), pos.getZ());
			case ARROW_E -> sendGhostPos(pos.getX() + 1, pos.getY(), pos.getZ());
		}
	}

	private void cancelGhostWithRefund() {
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.civcraft.network.CancelGhostPayload());
		if (com.civcraft.client.building.GhostState.kind
				== com.civcraft.network.SpawnGhostPayload.KIND_TOWNHALL) {
			com.civcraft.client.resource.ResourceState.food       += com.civcraft.client.resource.ResourceState.TOWN_HALL_FOOD;
			com.civcraft.client.resource.ResourceState.production += com.civcraft.client.resource.ResourceState.TOWN_HALL_PRODUCTION;
		}
	}

	private static boolean canSend(Minecraft client) {
		return client.player != null && client.player.connection != null
				&& client.getConnection() != null;
	}

	private void sendGhostPos(int x, int y, int z) {
		Minecraft client = Minecraft.getInstance();
		if (!canSend(client)) return;
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.civcraft.network.UpdateGhostPosPayload(x, y, z));
	}

	private void sendGhostDrag(Minecraft client, double mx, double my) {
		net.minecraft.world.phys.Vec3 target = CameraMath.cursorToBlock(client, mx, my);
		if (target == null) return;
		int px = (int) Math.floor(target.x);
		int pz = (int) Math.floor(target.z);
		// Only send if changed from last-known pos to avoid packet spam.
		if (px == com.civcraft.client.building.GhostState.pos.getX()
				&& pz == com.civcraft.client.building.GhostState.pos.getZ()) return;
		int py = (int) Math.floor(target.y);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.civcraft.network.UpdateGhostPosPayload(px, py, pz));
	}

	private void issueMoveOrder(Minecraft client) {
		if (client.player == null) return;
		if (SelectionState.selected.isEmpty()) {
			client.player.displayClientMessage(
					Component.literal("§7Nothing selected — drag LMB over units first."), true);
			return;
		}
		Vec3 target = CameraMath.cursorToBlock(
				client, client.mouseHandler.xpos(), client.mouseHandler.ypos());
		if (target == null) {
			client.player.displayClientMessage(
					Component.literal("§cCannot aim there."), true);
			return;
		}
		// Send a single networking packet; the server does per-unit pathfinding
		// so movement is real walking with ground contact + formation spread.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.civcraft.network.MoveOrderPayload(
						new java.util.ArrayList<>(SelectionState.selected),
						target.x, target.y, target.z));
		client.player.displayClientMessage(
				Component.literal(String.format(java.util.Locale.ROOT,
						"§6March to (%.0f, %.0f)", target.x, target.z)), true);
	}

	private void onIsoToggled(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) return;

		if (TopDownMode.active) {
			TopDownMode.anchorX = player.getX();
			TopDownMode.anchorY = player.getY();
			TopDownMode.anchorZ = player.getZ();

			client.mouseHandler.releaseMouse();
			sendCommand(client, "gamemode spectator");
			player.displayClientMessage(
					Component.literal("§6RTS ON  ·  ЛКМ выделение  ·  ПКМ приказ  ·  СКМ pan  ·  Shift+СКМ поворот  ·  колесо зум"), true);
		} else {
			SelectionState.selected.clear();
			client.mouseHandler.grabMouse();
			sendCommand(client, "gamemode creative");
			player.displayClientMessage(Component.literal("§7RTS OFF"), true);
		}
	}

	private void sendCommand(Minecraft client, String cmd) {
		if (client.player != null && client.player.connection != null) {
			client.player.connection.sendCommand(cmd);
		}
	}

	private void panCameraByKeys(Minecraft client) {
		if (client.options == null) return;
		double dx = 0, dz = 0;
		boolean w = client.options.keyUp.isDown();
		boolean s = client.options.keyDown.isDown();
		boolean a = client.options.keyLeft.isDown();
		boolean d = client.options.keyRight.isDown();

		double yawRad = Math.toRadians(TopDownMode.yaw);
		double fx = -Math.sin(yawRad);
		double fz = Math.cos(yawRad);
		if (w) { dx += fx; dz += fz; }
		if (s) { dx -= fx; dz -= fz; }
		// A/D inverted per user preference: A pans right, D pans left.
		if (a) { dx += fz; dz -= fx; }
		if (d) { dx -= fz; dz += fx; }

		double len = Math.hypot(dx, dz);
		if (len > 0) {
			dx = dx / len * TopDownMode.panSpeed;
			dz = dz / len * TopDownMode.panSpeed;
			TopDownMode.anchorX += dx;
			TopDownMode.anchorZ += dz;
		}
	}

	/**
	 * Middle-mouse drag: pan the camera anchor by tracking the mouse delta since
	 * MMB went down. Hold Shift to rotate yaw instead (delta X → degrees).
	 */
	private void pollMiddleMouse(Minecraft client) {
		long window = client.getWindow().handle();
		boolean mmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
		boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
		double mx = client.mouseHandler.xpos();
		double my = client.mouseHandler.ypos();

		if (mmb && !prevMmb) {
			mmbStartX = mx;
			mmbStartY = my;
			mmbStartAnchorX = TopDownMode.anchorX;
			mmbStartAnchorZ = TopDownMode.anchorZ;
			mmbStartYaw = TopDownMode.yaw;
			mmbWasRotate = shift;
		}
		if (mmb) {
			double dx = mx - mmbStartX;
			double dy = my - mmbStartY;
			if (mmbWasRotate) {
				TopDownMode.yaw = (mmbStartYaw + (float) (dx * TopDownMode.mouseYawSensitivity) + 720f) % 360f;
			} else {
				// Convert screen delta to world delta — account for camera yaw
				// so a horizontal drag always moves the map horizontally on screen.
				double yawRad = Math.toRadians(TopDownMode.yaw);
				double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);  // forward
				double sx = fz, sz = -fx;                              // side (right)
				double wx = (sx * dx + fx * dy) * TopDownMode.mousePanSensitivity;
				double wz = (sz * dx + fz * dy) * TopDownMode.mousePanSensitivity;
				TopDownMode.anchorX = mmbStartAnchorX + wx;
				TopDownMode.anchorZ = mmbStartAnchorZ + wz;
			}
		}
		prevMmb = mmb;
	}

	private void holdCursorFree(Minecraft client) {
		client.mouseHandler.releaseMouse();
	}

	private static final double SQUAD_LINK_RADIUS = 10.0;

	private void resolveSelection(Minecraft client) {
		if (client.level == null) return;
		double x0 = Math.min(SelectionState.startX, SelectionState.currentX);
		double x1 = Math.max(SelectionState.startX, SelectionState.currentX);
		double y0 = Math.min(SelectionState.startY, SelectionState.currentY);
		double y1 = Math.max(SelectionState.startY, SelectionState.currentY);

		// First pass: gather entities the user directly hit — either inside the
		// rectangle, or (for a click) under the click point within a small pixel
		// radius so clicking on a mob actually hits it.
		java.util.List<Entity> hits = new java.util.ArrayList<>();
		boolean isClick = (x1 - x0) < 4 && (y1 - y0) < 4;
		double clickPadding = 20.0;

		for (Entity e : client.level.entitiesForRendering()) {
			if (!isSquadMember(e)) continue;
			float[] s = CameraMath.worldToScreen(client, e.getX(), e.getY() + 0.9, e.getZ());
			if (s == null) continue;
			boolean in;
			if (isClick) {
				in = Math.abs(s[0] - x0) <= clickPadding && Math.abs(s[1] - y0) <= clickPadding;
			} else {
				in = s[0] >= x0 && s[0] <= x1 && s[1] >= y0 && s[1] <= y1;
			}
			if (in) hits.add(e);
		}

		if (hits.isEmpty()) {
			// No entity under the click — try to pick a Town Hall spire instead.
			if (isClick) {
				net.minecraft.core.BlockPos bp = findTownHallUnderCursor(client, x0, y0);
				if (bp != null) {
					SelectionState.setBuilding(bp);
					if (client.player != null) {
						client.player.displayClientMessage(
								Component.literal("§6Town Hall selected"), true);
					}
					return;
				}
			}
			SelectionState.clearAll();
			if (client.player != null) {
				client.player.displayClientMessage(Component.literal("§7Deselected"), true);
			}
			return;
		}

		// Second pass: expand each direct hit to its full squad (all squad
		// members within SQUAD_LINK_RADIUS). Keep selection kind consistent —
		// a builder hit yields a builder squad, a settler hit a settler squad.
		boolean anyBuilder = hits.stream().anyMatch(CivcraftClient::isBuilder);
		java.util.Set<UUID> squad = new java.util.HashSet<>();
		for (Entity hit : hits) {
			for (Entity other : client.level.entitiesForRendering()) {
				if (!isSquadMember(other)) continue;
				if (isBuilder(other) != anyBuilder) continue;
				if (other.distanceTo(hit) <= SQUAD_LINK_RADIUS) {
					squad.add(other.getUUID());
				}
			}
		}
		SelectionState.setSquad(squad,
				anyBuilder ? SelectionState.Kind.BUILDER_SQUAD : SelectionState.Kind.SQUAD);

		if (client.player != null) {
			client.player.displayClientMessage(
					Component.literal("§bSelected §e" + squad.size() + "§b units"), true);
		}
	}

	/**
	 * Search a 6-block radius around the cursor's ground-plane hit point for a
	 * Town Hall block. Returns its position or null.
	 */
	private net.minecraft.core.BlockPos findTownHallUnderCursor(Minecraft client, double mouseX, double mouseY) {
		if (client.level == null) return null;
		net.minecraft.world.phys.Vec3 ground = CameraMath.cursorToBlock(client, mouseX, mouseY);
		if (ground == null) return null;
		int cx = (int) Math.floor(ground.x);
		int cz = (int) Math.floor(ground.z);
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				for (int dy = -2; dy <= 8; dy++) {
					net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(cx + dx, (int) ground.y + dy, cz + dz);
					if (client.level.getBlockState(p).getBlock() == com.civcraft.registry.ModBlocks.TOWN_HALL) {
						return p;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Client-side heuristic for "is this entity one of ours?"
	 *
	 * Server-side tags (addTag) are not synced to clients in 1.21, so we cannot
	 * rely on them here. Every squad member we spawn via Settler's Charter
	 * gets one of a fixed set of custom names, so matching on that gives us a
	 * stable identity check without extra packet plumbing.
	 */
	public static boolean isSquadMember(Entity e) {
		if (e.getCustomName() == null) return false;
		String name = e.getCustomName().getString();
		if (name.startsWith("⚔ ") || name.startsWith("⛏ ") || name.startsWith("🪓 ")) return true;
		// Backwards-compat with squads spawned before we added the ⚔ prefix.
		return name.equals("Старейшина") || name.equals("Поселенец")
				|| name.equals("Колонист") || name.equals("Мул");
	}

	public static boolean isBuilder(Entity e) {
		if (e.getCustomName() == null) return false;
		return e.getCustomName().getString().startsWith("⛏ ");
	}

	private void syncGlowWithSelection(Minecraft client) {
		if (client.level == null) return;
		for (Entity e : client.level.entitiesForRendering()) {
			if (!isSquadMember(e)) continue;
			boolean wantGlow = SelectionState.selected.contains(e.getUUID());
			if (e.hasGlowingTag() != wantGlow) {
				e.setGlowingTag(wantGlow);
			}
		}
	}

}
