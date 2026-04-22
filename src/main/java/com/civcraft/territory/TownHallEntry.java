package com.civcraft.territory;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * One registered town hall. Identity fields ({@code townHallId},
 * {@code playerId}, {@code pos}, {@code createdAt}) are fixed at
 * registration; the economic fields ({@code population},
 * {@code foodMeter}) grow over turns and drive the per-city HUD.
 *
 * <p>Records are immutable, so mutations replace the entry in
 * {@link TownHallRegistry}'s list with a copy produced by
 * {@link #withPopulation} / {@link #withFoodMeter}.
 */
public record TownHallEntry(
		int townHallId,
		UUID playerId,
		BlockPos pos,
		long createdAt,
		int population,
		int foodMeter
) {
	public static final int CLAIM_RADIUS = 32;
	public static final int CLAIM_RADIUS_SQ = CLAIM_RADIUS * CLAIM_RADIUS;

	/** Default yields from the hall itself — matches Concept.md §9. */
	public static final int TOWN_HALL_FOOD_YIELD = 2;
	public static final int TOWN_HALL_PRODUCTION_YIELD = 1;
	/** Housing cap from the hall alone, before any Houses are built. */
	public static final int TOWN_HALL_HOUSING_CAP = 2;

	/** Civ-style growth cost: slots get progressively harder to fill. */
	public static int foodThresholdFor(int population) {
		return 15 + population * 8;
	}

	/** Convenience for newly-registered halls: pop = 1, empty food meter. */
	public TownHallEntry(int id, UUID player, BlockPos pos, long createdAt) {
		this(id, player, pos, createdAt, 1, 0);
	}

	public boolean contains(int worldX, int worldZ) {
		int dx = worldX - pos.getX();
		int dz = worldZ - pos.getZ();
		return dx * dx + dz * dz <= CLAIM_RADIUS_SQ;
	}

	/**
	 * Per-turn yields — Concept.md §9 / §5.2. The Town Hall on its own
	 * produces +2 food and +1 production; each citizen adds +1 production
	 * but no extra food (food boosts will come from farm-like buildings
	 * once they exist).
	 */
	public int foodYield() {
		return TOWN_HALL_FOOD_YIELD;
	}

	public int productionYield() {
		return TOWN_HALL_PRODUCTION_YIELD + population;
	}

	public int housingCap() {
		return TOWN_HALL_HOUSING_CAP;
	}

	public TownHallEntry withPopulation(int newPop) {
		return new TownHallEntry(townHallId, playerId, pos, createdAt, newPop, foodMeter);
	}

	public TownHallEntry withFoodMeter(int newMeter) {
		return new TownHallEntry(townHallId, playerId, pos, createdAt, population, newMeter);
	}
}
