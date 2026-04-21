package com.civcraft.turn;

/**
 * Global turn state for the world. Very simple for Phase 1 — single shared
 * calendar + era (no per-player sequencing yet). Later phases will split this
 * per civilization.
 */
public final class TurnState {
	public static int turnNumber = 1;
	public static int year = -4000;
	public static Era era = Era.ANCIENT;

	private TurnState() {}

	/** Advance by one turn: add yearsPerTurn and re-evaluate the era. */
	public static void advance() {
		turnNumber++;
		year += era.yearsPerTurn;
		era = Era.forYear(year);
	}
}
