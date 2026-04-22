package com.civcraft.territory;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-authoritative list of every town hall that has ever been built in
 * the current world. Ownership of a column is decided by walking this list
 * and picking the earliest-created hall whose claim disc covers that XZ —
 * so we never have to store anything per-block.
 *
 * <p>In-memory only for now; persistence to a {@code SavedData} attachment
 * will come when the world needs to survive a server restart.
 */
public final class TownHallRegistry {
	private static final List<TownHallEntry> ENTRIES = new ArrayList<>();
	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

	private TownHallRegistry() {}

	public static synchronized TownHallEntry register(UUID player, BlockPos pos) {
		TownHallEntry entry = new TownHallEntry(
				NEXT_ID.getAndIncrement(), player, pos.immutable(), System.currentTimeMillis());
		ENTRIES.add(entry);
		return entry;
	}

	public static synchronized List<TownHallEntry> snapshot() {
		return new ArrayList<>(ENTRIES);
	}

	public static synchronized int size() {
		return ENTRIES.size();
	}

	public static synchronized TownHallEntry getExactlyAt(net.minecraft.core.BlockPos pos) {
		for (TownHallEntry e : ENTRIES) {
			if (e.pos().equals(pos)) return e;
		}
		return null;
	}

	public static synchronized void replaceEntry(TownHallEntry updated) {
		for (int i = 0; i < ENTRIES.size(); i++) {
			if (ENTRIES.get(i).townHallId() == updated.townHallId()) {
				ENTRIES.set(i, updated);
				return;
			}
		}
	}

	/**
	 * Find the town hall that owns the given XZ column, or {@code null} if
	 * no hall's claim disc covers it. Ties broken by earliest {@code createdAt}.
	 */
	public static synchronized TownHallEntry owningHall(int worldX, int worldZ) {
		TownHallEntry best = null;
		for (TownHallEntry e : ENTRIES) {
			if (!e.contains(worldX, worldZ)) continue;
			if (best == null || e.createdAt() < best.createdAt()) best = e;
		}
		return best;
	}

	public static List<TownHallEntry> unmodifiableView() {
		return Collections.unmodifiableList(ENTRIES);
	}

	public static synchronized void replaceAll(List<TownHallEntry> fresh) {
		ENTRIES.clear();
		ENTRIES.addAll(fresh);
		int maxId = 0;
		for (TownHallEntry e : fresh) if (e.townHallId() > maxId) maxId = e.townHallId();
		NEXT_ID.set(maxId + 1);
	}

	public static synchronized void restoreNextId(int nextId) {
		NEXT_ID.set(Math.max(1, nextId));
	}

	public static int peekNextId() {
		return NEXT_ID.get();
	}
}
