package com.civcraft.client.turn;

import com.civcraft.turn.Era;

/** Mirror of the server's TurnState for HUD display. */
public final class TurnInfo {
	public static int turn = 1;
	public static int year = -4000;
	public static Era era  = Era.ANCIENT;

	private TurnInfo() {}

	public static String yearLabel() {
		return year < 0 ? (-year) + " до н.э." : year + " н.э.";
	}
}
