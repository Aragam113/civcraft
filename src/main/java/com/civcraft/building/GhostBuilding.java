package com.civcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative state for one pending building ghost. One player may
 * only have one active ghost at a time.
 *
 * <p>When a ghost is confirmed into a multi-turn construction we snapshot
 * the final structure into {@link #intendedBlocks} (positions that the
 * build writes to and their target states) plus a bounding box for
 * overlap-rejection. Only the bottom {@link #revealedLayers} Y-slices
 * are visible in the world at any time; the rest are reverted to the
 * terrain they replaced and restored layer-by-layer as production
 * progress passes each fractional threshold.
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
	public int target;               // production points needed; 0 for instant (townhall)
	public final List<UUID> units = new ArrayList<>();

	/** Per-turn-revealed construction — null for instant builds. */
	public Map<BlockPos, BlockState> intendedBlocks;    // pos → final state
	public Map<BlockPos, BlockState> previousBlocks;    // pos → what the terrain had before confirm
	public int revealedLayers;                          // layers 1..N already rendered as real blocks
	public int totalLayers;                             // total Y-layers spanned by the build
	public int minY;                                    // world-Y of the bottom layer
	public int bboxMinX, bboxMinZ, bboxMaxX, bboxMaxZ, bboxMaxY;  // reservation rectangle for overlap checks

	public GhostBuilding(UUID owner, byte kind, BlockPos pos, int target, List<UUID> units) {
		this.owner = owner;
		this.kind = kind;
		this.pos = pos;
		this.originX = pos.getX();
		this.originZ = pos.getZ();
		this.target = target;
		this.units.addAll(units);
	}

	public void initPlannedLayers(Map<BlockPos, BlockState> intended, Map<BlockPos, BlockState> previous) {
		if (intended.isEmpty()) {
			this.intendedBlocks = new HashMap<>();
			this.previousBlocks = new HashMap<>();
			this.totalLayers = 1;
			this.minY = pos.getY();
			this.bboxMinX = pos.getX();
			this.bboxMaxX = pos.getX();
			this.bboxMinZ = pos.getZ();
			this.bboxMaxZ = pos.getZ();
			this.bboxMaxY = pos.getY();
			this.revealedLayers = 1;
			return;
		}
		this.intendedBlocks = new HashMap<>(intended);
		this.previousBlocks = new HashMap<>(previous);
		int minX =  Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minZ =  Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		int mnY  =  Integer.MAX_VALUE, mxY  = Integer.MIN_VALUE;
		for (BlockPos p : intended.keySet()) {
			if (p.getX() < minX) minX = p.getX();
			if (p.getX() > maxX) maxX = p.getX();
			if (p.getZ() < minZ) minZ = p.getZ();
			if (p.getZ() > maxZ) maxZ = p.getZ();
			if (p.getY() < mnY)  mnY  = p.getY();
			if (p.getY() > mxY)  mxY  = p.getY();
		}
		this.bboxMinX = minX;
		this.bboxMaxX = maxX;
		this.bboxMinZ = minZ;
		this.bboxMaxZ = maxZ;
		this.bboxMaxY = mxY;
		this.minY = mnY;
		this.totalLayers = mxY - mnY + 1;
		this.revealedLayers = 1;
	}
}
