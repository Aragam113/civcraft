package com.civcraft.client.building;

import com.civcraft.client.territory.ClientTownHalls;
// ClientConstructionSites sits in the same package; no explicit import needed.
import com.civcraft.network.SpawnGhostPayload;
import com.civcraft.territory.TownHallEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Client-side legality check for the building ghost. Drives both the
 * red overlay on the preview and — once the server echoes the same
 * logic — the confirm-time rejection. Two rules:
 *
 * <ol>
 *   <li>No block of the ghost shape may overlap an existing non-air,
 *       non-replaceable block (covers both "my" structures and other
 *       players'; terrain foliage that replaces on place is fine).</li>
 *   <li>Town halls additionally need a 15-block breathing room from
 *       any foreign town hall's claim disc edge, i.e. the ghost's XZ
 *       distance to another player's hall must be at least
 *       {@code CLAIM_RADIUS + 15}.</li>
 * </ol>
 */
public final class GhostValidator {
	public static final int FOREIGN_BUFFER = 28;
	/** A new town hall must stay at least this many blocks clear of the
	 *  claim-disc edge of any EXISTING own town hall — keeps two of your
	 *  own cities from crowding each other right up to the border. */
	public static final int OWN_BUFFER = 18;

	private GhostValidator() {}

	public static boolean isForbidden(Minecraft mc, int px, int py, int pz, byte kind) {
		if (mc.level == null || mc.player == null) return false;
		UUID me = mc.player.getUUID();

		// Build the set of currently-online player UUIDs up front — the TH
		// buffer and the foreign-territory footprint check both need it.
		var conn = mc.getConnection();
		java.util.Set<UUID> online = new java.util.HashSet<>();
		if (conn != null) {
			for (var info : conn.getOnlinePlayers()) online.add(info.getProfile().id());
		}

		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (var gb : GhostState.shape()) {
			int wx = px + gb.dx();
			int wy = py + gb.dy();
			int wz = pz + gb.dz();

			// 1. Only reject on overlap with another PLAYER-PLACED mod
			//    structure. Vanilla terrain (trees, stone, grass) is OK —
			//    those blocks get overwritten when the ghost is built.
			p.set(wx, wy, wz);
			var existingBlock = mc.level.getBlockState(p).getBlock();
			if (existingBlock == com.civcraft.registry.ModBlocks.TOWN_HALL
					|| existingBlock == com.civcraft.registry.ModBlocks.SMITHY
					|| existingBlock == com.civcraft.registry.ModBlocks.SAWMILL
					|| existingBlock == com.civcraft.registry.ModBlocks.QUARRY
					|| existingBlock == com.civcraft.registry.ModBlocks.STOREHOUSE) {
				return true;
			}

			// 2. Ghost footprint must not punch into FOREIGN territory
			//    (orphan halls with offline owners are ignored).
			TownHallEntry owner = ClientTownHalls.owningHall(wx, wz);
			if (owner != null
					&& !owner.playerId().equals(me)
					&& online.contains(owner.playerId())) {
				return true;
			}

			// 3. Reject overlap with a construction reservation — these are
			//    in-progress buildings whose upper layers haven't been
			//    placed yet but still belong to someone's site.
			if (ClientConstructionSites.contains(wx, wy, wz)) return true;
		}

		// 3. Town halls keep two different buffers: 15 blocks past any
		//    foreign claim disc, 5 blocks past any of our OWN claim discs
		//    (so the player's cities can't crowd each other at the border).
		//    Orphan halls (owner not in the online roster — usually a stale
		//    UUID from a previous dev session) are ignored.
		if (kind == SpawnGhostPayload.KIND_TOWNHALL) {
			int foreignMinSq = (TownHallEntry.CLAIM_RADIUS + FOREIGN_BUFFER)
					* (TownHallEntry.CLAIM_RADIUS + FOREIGN_BUFFER);
			int ownMinSq = (TownHallEntry.CLAIM_RADIUS + OWN_BUFFER)
					* (TownHallEntry.CLAIM_RADIUS + OWN_BUFFER);
			for (TownHallEntry th : ClientTownHalls.all()) {
				int dx = px - th.pos().getX();
				int dz = pz - th.pos().getZ();
				int d2 = dx * dx + dz * dz;
				if (th.playerId().equals(me)) {
					if (d2 < ownMinSq) return true;
				} else if (online.contains(th.playerId())) {
					if (d2 < foreignMinSq) return true;
				}
			}
		}
		return false;
	}
}
