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
	private static KeyMapping rotateLeftKey;
	private static KeyMapping rotateRightKey;
	private static KeyMapping perkKey;
	private static KeyMapping nextTurnKey;

	private boolean prevLmb = false;
	private boolean prevRmb = false;

	@Override
	public void onInitializeClient() {
		Civcraft.LOGGER.info("[CivCraft] Initializing client-side systems");

		toggleCameraKey = bind("key.civcraft.toggle_camera", GLFW.GLFW_KEY_C);
		rotateLeftKey   = bind("key.civcraft.rotate_left",  GLFW.GLFW_KEY_Z);
		rotateRightKey  = bind("key.civcraft.rotate_right", GLFW.GLFW_KEY_X);
		perkKey         = bind("key.civcraft.perk",         GLFW.GLFW_KEY_Q);
		nextTurnKey     = bind("key.civcraft.next_turn",    GLFW.GLFW_KEY_SPACE);

		ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
		CivcraftHud.register();
		com.civcraft.client.render.OverlayRenderer.register();
		EntityRendererRegistry.register(ModEntities.SETTLER, SettlerRenderer::new);
	}

	private static KeyMapping bind(String key, int glfwKey) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(
				key, InputConstants.Type.KEYSYM, glfwKey, KeyMapping.Category.MISC));
	}

	private void clientTick(Minecraft client) {
		while (toggleCameraKey.consumeClick()) {
			if (client.player == null) continue;
			TopDownMode.toggle();
			onIsoToggled(client);
		}
		if (TopDownMode.active) {
			panCameraByKeys(client);
			rotateByKeys();
			holdCursorFree(client);
			pollMouseButtons(client);
			syncGlowWithSelection(client);
			handlePerkKey(client);
			handleNextTurnKey(client);
		}
	}

	private void handleNextTurnKey(Minecraft client) {
		while (nextTurnKey.consumeClick()) {
			sendNextTurn(client);
		}
	}

	private void sendNextTurn(Minecraft client) {
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.civcraft.network.NextTurnPayload());
		com.civcraft.client.hud.CivcraftHud.startTurnAnimation();
		if (client.player != null) {
			client.player.displayClientMessage(
					Component.literal("§eНаступает ночь..."), true);
		}
	}

	private void handlePerkKey(Minecraft client) {
		while (perkKey.consumeClick()) {
			firePerk(client);
		}
	}

	private void firePerk(Minecraft client) {
		switch (SelectionState.kind) {
			case SQUAD -> {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.civcraft.network.FoundTownHallPayload(
								new java.util.ArrayList<>(SelectionState.selected)));
				SelectionState.clearAll();
				if (client.player != null) {
					client.player.displayClientMessage(
							Component.literal("§6Ратуша закладывается..."), true);
				}
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

	private void pollMouseButtons(Minecraft client) {
		long window = client.getWindow().handle();
		boolean lmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean rmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		double mx = client.mouseHandler.xpos();
		double my = client.mouseHandler.ypos();

		if (lmb && !prevLmb) {
			// If the click started over the perk button, fire the perk and
			// skip selection to avoid a spurious drag.
			if (com.civcraft.client.hud.CivcraftHud.isMouseOverPlanet(client)) {
				sendNextTurn(client);
				prevLmb = true;
				return;
			}
			if (com.civcraft.client.hud.CivcraftHud.isMouseOverPerk(client)
					&& SelectionState.kind != SelectionState.Kind.NONE) {
				firePerk(client);
				prevLmb = true;
				return;
			}
			SelectionState.begin(mx, my);
			Civcraft.LOGGER.info("[CivCraft] LMB DOWN at ({}, {})", mx, my);
		} else if (!lmb && prevLmb) {
			SelectionState.end();
			Civcraft.LOGGER.info("[CivCraft] LMB UP at ({}, {})", mx, my);
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

	private void issueMoveOrder(Minecraft client) {
		if (client.player == null) return;
		if (SelectionState.selected.isEmpty()) {
			client.player.displayClientMessage(
					Component.literal("§7Nothing selected — drag LMB over units first."), true);
			return;
		}
		Vec3 target = CameraMath.cursorToGround(
				client, client.mouseHandler.xpos(), client.mouseHandler.ypos(), TopDownMode.anchorY);
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
					Component.literal("§6RTS ON  ·  LMB drag = select  ·  RMB = move  ·  Z/X rotate  ·  scroll zoom"), true);
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

	private void rotateByKeys() {
		while (rotateLeftKey.consumeClick()) {
			TopDownMode.yaw = (TopDownMode.yaw + 360f - TopDownMode.yawStep) % 360f;
		}
		while (rotateRightKey.consumeClick()) {
			TopDownMode.yaw = (TopDownMode.yaw + TopDownMode.yawStep) % 360f;
		}
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
		// members within SQUAD_LINK_RADIUS).
		java.util.Set<UUID> squad = new java.util.HashSet<>();
		for (Entity hit : hits) {
			for (Entity other : client.level.entitiesForRendering()) {
				if (!isSquadMember(other)) continue;
				if (other.distanceTo(hit) <= SQUAD_LINK_RADIUS) {
					squad.add(other.getUUID());
				}
			}
		}
		SelectionState.setSquad(squad);

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
		net.minecraft.world.phys.Vec3 ground = CameraMath.cursorToGround(
				client, mouseX, mouseY, TopDownMode.anchorY);
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
		if (name.startsWith("⚔ ")) return true;
		// Backwards-compat with squads spawned before we added the ⚔ prefix.
		return name.equals("Старейшина") || name.equals("Поселенец")
				|| name.equals("Колонист") || name.equals("Мул");
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
