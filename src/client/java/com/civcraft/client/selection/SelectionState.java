package com.civcraft.client.selection;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side selection state for the RTS UI.
 *
 * We support two kinds of selection:
 *   - SQUAD: a set of entity UUIDs (villagers + mule + leader).
 *   - BUILDING: a single BlockPos of a Town Hall spire.
 * Exactly one kind is active at a time; picking the other clears the first.
 */
public final class SelectionState {
	public enum Kind { NONE, SQUAD, BUILDING }

	public static boolean dragging = false;
	public static double startX = 0, startY = 0, currentX = 0, currentY = 0;

	public static Kind kind = Kind.NONE;
	public static final Set<UUID> selected = new HashSet<>();
	public static BlockPos selectedBuilding = null;

	private SelectionState() {}

	public static void begin(double x, double y) {
		dragging = true;
		startX = x; startY = y; currentX = x; currentY = y;
	}
	public static void update(double x, double y) { currentX = x; currentY = y; }
	public static void end() { dragging = false; }

	public static void clearAll() {
		selected.clear();
		selectedBuilding = null;
		kind = Kind.NONE;
	}

	public static void setSquad(Set<UUID> uuids) {
		selectedBuilding = null;
		selected.clear();
		selected.addAll(uuids);
		kind = uuids.isEmpty() ? Kind.NONE : Kind.SQUAD;
	}

	public static void setBuilding(BlockPos pos) {
		selected.clear();
		selectedBuilding = pos;
		kind = pos == null ? Kind.NONE : Kind.BUILDING;
	}
}
