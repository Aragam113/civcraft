package com.civcraft.client.resource;

/**
 * Civilization-style economy: Food / Production / Gold / Science / Culture.
 * Authoritative values arrive from the server via ResourceSyncPayload.
 */
public final class ResourceState {
	public static final int TOWN_HALL_FOOD       = 30;
	public static final int TOWN_HALL_PRODUCTION = 40;

	public static int food       = TOWN_HALL_FOOD;
	public static int production = TOWN_HALL_PRODUCTION;
	public static int gold       = 10;
	public static int science    = 0;
	public static int culture    = 0;

	private ResourceState() {}

	public static boolean canAffordTownHall() {
		return food >= TOWN_HALL_FOOD && production >= TOWN_HALL_PRODUCTION;
	}

	public static void deductTownHall() {
		food       -= TOWN_HALL_FOOD;
		production -= TOWN_HALL_PRODUCTION;
	}
}
