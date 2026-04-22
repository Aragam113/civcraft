package com.civcraft.network;

import com.civcraft.territory.TownHallEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → client: full snapshot of the town-hall registry. Re-sent on
 * join and whenever a hall is added or removed. Clients replace their
 * mirror wholesale on each receive — there are rarely more than a
 * handful of halls so a delta protocol isn't worth the complexity yet.
 */
public record TownHallSyncPayload(List<TownHallEntry> entries) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<TownHallSyncPayload> ID =
			new CustomPacketPayload.Type<>(
					Identifier.fromNamespaceAndPath("civcraft", "townhall_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TownHallSyncPayload> CODEC =
			StreamCodec.of(
					(buf, payload) -> {
						buf.writeVarInt(payload.entries.size());
						for (TownHallEntry e : payload.entries) {
							buf.writeVarInt(e.townHallId());
							buf.writeUUID(e.playerId());
							buf.writeBlockPos(e.pos());
							buf.writeLong(e.createdAt());
							buf.writeVarInt(e.population());
							buf.writeVarInt(e.foodMeter());
						}
					},
					buf -> {
						int n = buf.readVarInt();
						List<TownHallEntry> list = new ArrayList<>(n);
						for (int i = 0; i < n; i++) {
							int id = buf.readVarInt();
							UUID p = buf.readUUID();
							BlockPos pos = buf.readBlockPos();
							long t = buf.readLong();
							int pop = buf.readVarInt();
							int food = buf.readVarInt();
							list.add(new TownHallEntry(id, p, pos, t, pop, food));
						}
						return new TownHallSyncPayload(list);
					});

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
