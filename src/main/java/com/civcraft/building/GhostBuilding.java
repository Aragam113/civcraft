package com.civcraft.building;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative state for one pending building ghost. One player may
 * only have one active ghost at a time.
 */
public class GhostBuilding {
	public final UUID owner;
	public final byte kind;          // SpawnGhostPayload KIND_*
	public BlockPos pos;
	/** World X/Z where the squad stood on spawn; dragging is clamped to a radius from here. */
	public final int originX;
	public final int originZ;
	public boolean confirmed = false;
	public int progress = 0;
	public int target;               // delivery trips needed; 0 for instant (townhall)
	public final List<UUID> units = new ArrayList<>();

	public GhostBuilding(UUID owner, byte kind, BlockPos pos, int target, List<UUID> units) {
		this.owner = owner;
		this.kind = kind;
		this.pos = pos;
		this.originX = pos.getX();
		this.originZ = pos.getZ();
		this.target = target;
		this.units.addAll(units);
	}
}
