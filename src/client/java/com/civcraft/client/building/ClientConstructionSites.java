package com.civcraft.client.building;

import com.civcraft.network.ConstructionSitesPayload;

import java.util.List;

/**
 * Client mirror of every active construction's reservation rectangle.
 * {@link GhostValidator} consults this so the ghost preview shows red
 * when the player drops a new building over an in-progress site.
 */
public final class ClientConstructionSites {
	private static volatile List<ConstructionSitesPayload.Site> SITES = List.of();

	private ClientConstructionSites() {}

	public static void setAll(List<ConstructionSitesPayload.Site> fresh) {
		SITES = List.copyOf(fresh);
	}

	public static boolean contains(int worldX, int worldY, int worldZ) {
		for (var s : SITES) {
			if (s.contains(worldX, worldY, worldZ)) return true;
		}
		return false;
	}
}
