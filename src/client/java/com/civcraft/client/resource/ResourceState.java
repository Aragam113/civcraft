package com.civcraft.client.resource;

/**
 * Client-side resource counters shown in the top HUD bar. Starting values are
 * exactly enough to found one Town Hall — see TOWN_HALL_* cost constants.
 */
public final class ResourceState {
	public static final int TOWN_HALL_FOOD  = 50;
	public static final int TOWN_HALL_WOOD  = 100;
	public static final int TOWN_HALL_STONE = 50;

	public static int food  = TOWN_HALL_FOOD;
	public static int wood  = TOWN_HALL_WOOD;
	public static int stone = TOWN_HALL_STONE;
	public static int coal  = 0;
	public static int iron  = 0;
	public static int gold  = 0;

	private ResourceState() {}

	public static boolean canAffordTownHall() {
		return food >= TOWN_HALL_FOOD && wood >= TOWN_HALL_WOOD && stone >= TOWN_HALL_STONE;
	}

	public static void deductTownHall() {
		food  -= TOWN_HALL_FOOD;
		wood  -= TOWN_HALL_WOOD;
		stone -= TOWN_HALL_STONE;
	}
}
