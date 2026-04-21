package com.civcraft.client.resource;

/**
 * Civilization-style economy: Food / Production / Gold / Science / Culture.
 * Authoritative values arrive from the server via ResourceSyncPayload.
 */
public final class ResourceState {
	public static final int TOWN_HALL_FOOD = 30;

	public static int food       = TOWN_HALL_FOOD;
	/** Per-turn production yield (not a stored balance). */
	public static int production = 0;
	public static int gold       = 10;
	/** Per-turn science yield. */
	public static int science    = 0;
	/** Per-turn culture yield. */
	public static int culture    = 0;

	private ResourceState() {}

	public static boolean canAffordTownHall() {
		return food >= TOWN_HALL_FOOD;
	}

	public static void deductTownHall() {
		food -= TOWN_HALL_FOOD;
	}
}
