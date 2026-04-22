package com.civcraft.territory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * World-scoped persistent state for civcraft: the town-hall registry plus
 * counters for the next town-hall id and the next unit squad id. Backed by
 * the overworld's {@code DimensionDataStorage} so it survives restarts.
 *
 * <p>Lifetime: loaded on server start into in-memory registries
 * ({@link TownHallRegistry}, {@code SettlerCharterItem.SQUAD_COUNTER})
 * which stay authoritative at runtime. Any mutation that bumps a counter
 * or adds/removes a town hall calls {@link #sync} to push the fresh
 * snapshot back into this SavedData and mark it dirty — Minecraft's
 * auto-save then flushes to disk.
 */
public class CivcraftSavedData extends SavedData {

	private static final Codec<TownHallEntry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(TownHallEntry::townHallId),
			UUIDUtil.CODEC.fieldOf("player").forGetter(TownHallEntry::playerId),
			BlockPos.CODEC.fieldOf("pos").forGetter(TownHallEntry::pos),
			Codec.LONG.fieldOf("created").forGetter(TownHallEntry::createdAt),
			Codec.INT.optionalFieldOf("pop", 1).forGetter(TownHallEntry::population),
			Codec.INT.optionalFieldOf("food", 0).forGetter(TownHallEntry::foodMeter)
	).apply(i, TownHallEntry::new));

	public static final Codec<CivcraftSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
			ENTRY_CODEC.listOf().fieldOf("town_halls").forGetter(d -> d.townHalls),
			Codec.INT.fieldOf("next_town_hall_id").forGetter(d -> d.nextTownHallId),
			Codec.INT.fieldOf("next_squad_id").forGetter(d -> d.nextSquadId),
			Codec.INT.optionalFieldOf("turn_number", 1).forGetter(d -> d.turnNumber),
			Codec.INT.optionalFieldOf("year", -4000).forGetter(d -> d.year)
	).apply(i, CivcraftSavedData::new));

	public static final SavedDataType<CivcraftSavedData> TYPE = new SavedDataType<>(
			"civcraft_world",
			CivcraftSavedData::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	public final List<TownHallEntry> townHalls;
	public int nextTownHallId;
	public int nextSquadId;
	public int turnNumber;
	public int year;

	public CivcraftSavedData() {
		this(new ArrayList<>(), 1, 1, 1, -4000);
	}

	public CivcraftSavedData(List<TownHallEntry> halls, int nextTh, int nextSquad,
	                         int turnNumber, int year) {
		this.townHalls = new ArrayList<>(halls);
		this.nextTownHallId = Math.max(1, nextTh);
		this.nextSquadId = Math.max(1, nextSquad);
		this.turnNumber = Math.max(1, turnNumber);
		this.year = year;
	}

	public static CivcraftSavedData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Pushes the live registry + counters + calendar into this record
	 *  and marks it dirty so Minecraft's auto-save flushes it. */
	public synchronized void sync(List<TownHallEntry> halls, int nextTh, int nextSquad,
	                              int turnNumber, int year) {
		this.townHalls.clear();
		this.townHalls.addAll(halls);
		this.nextTownHallId = nextTh;
		this.nextSquadId = nextSquad;
		this.turnNumber = turnNumber;
		this.year = year;
		setDirty();
	}
}
