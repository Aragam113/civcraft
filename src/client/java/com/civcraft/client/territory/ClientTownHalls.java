package com.civcraft.client.territory;

import com.civcraft.territory.TownHallEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side mirror of the server's {@link com.civcraft.territory.TownHallRegistry}.
 * Rebuilt wholesale from each {@code TownHallSyncPayload} — the list is
 * tiny (a handful of halls at most) so a delta protocol is overkill.
 *
 * <p>Ownership queries are used by the lens shader's mask painter to
 * decide which blocks keep their colour.
 */
public final class ClientTownHalls {
	private static volatile List<TownHallEntry> ENTRIES = List.of();

	private ClientTownHalls() {}

	public static void setAll(List<TownHallEntry> fresh) {
		ENTRIES = List.copyOf(fresh);
	}

	public static List<TownHallEntry> all() {
		return ENTRIES;
	}

	/**
	 * Find the town hall whose claim disc owns the XZ column, honouring the
	 * "first to plant" tiebreak. Returns {@code null} when no hall covers it.
	 */
	public static TownHallEntry owningHall(int worldX, int worldZ) {
		TownHallEntry best = null;
		for (TownHallEntry e : ENTRIES) {
			if (!e.contains(worldX, worldZ)) continue;
			if (best == null || e.createdAt() < best.createdAt()) best = e;
		}
		return best;
	}

	/** True when any of this player's halls claim the column. */
	public static boolean isOwnedBy(UUID playerId, int worldX, int worldZ) {
		TownHallEntry owner = owningHall(worldX, worldZ);
		return owner != null && owner.playerId().equals(playerId);
	}

	public static TownHallEntry findAt(net.minecraft.core.BlockPos pos) {
		for (TownHallEntry e : ENTRIES) {
			if (e.pos().equals(pos)) return e;
		}
		return null;
	}
}
