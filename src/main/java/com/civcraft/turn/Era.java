package com.civcraft.turn;

/**
 * Civilization-style era progression. Each era sets how many years pass per
 * turn and the calendar threshold that auto-advances the world into it.
 */
public enum Era {
	ANCIENT    (40, -4000, "Античность"),
	CLASSICAL  (20, -1000, "Классика"),
	MEDIEVAL   (10,   500, "Средневековье"),
	RENAISSANCE( 5,  1500, "Ренессанс"),
	INDUSTRIAL ( 2,  1800, "Индустриализм"),
	MODERN     ( 1,  1900, "Новое время"),
	INFORMATION( 1,  1990, "Информационная эпоха");

	public final int yearsPerTurn;
	public final int startYear;
	public final String localized;

	Era(int ypt, int sy, String name) {
		this.yearsPerTurn = ypt;
		this.startYear    = sy;
		this.localized    = name;
	}

	/** Pick the latest era whose startYear ≤ given year. */
	public static Era forYear(int year) {
		Era best = ANCIENT;
		for (Era e : values()) {
			if (year >= e.startYear) best = e;
		}
		return best;
	}
}
