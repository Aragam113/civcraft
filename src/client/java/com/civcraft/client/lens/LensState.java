package com.civcraft.client.lens;

/**
 * Map-lens mode (Civ-style): NONE shows the world normally; the territorial
 * lenses tint everything outside the player's territory dark grey so the
 * owned zone stands out in its natural colours.
 */
public final class LensState {
	public enum Mode {
		NONE,
		CITY,   // 8-block discs around each owned town hall
		STATE;  // 24-block discs — "state borders"

		public Mode next() {
			return switch (this) {
				case NONE  -> CITY;
				case CITY  -> STATE;
				case STATE -> NONE;
			};
		}

		public String localized() {
			return switch (this) {
				case NONE  -> "Линза: нет";
				case CITY  -> "Границы города";
				case STATE -> "Границы государства";
			};
		}

		public int radius() {
			return switch (this) {
				case CITY -> 8;
				case STATE -> 24;
				default -> 0;
			};
		}
	}

	public static Mode mode = Mode.NONE;

	private LensState() {}

	public static void cycle() { mode = mode.next(); }
}
