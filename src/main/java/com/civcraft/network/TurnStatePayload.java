package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: current turn number, calendar year, and era ordinal. */
public record TurnStatePayload(int turn, int year, byte eraOrdinal)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TurnStatePayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "turn_state"));

	public static final StreamCodec<ByteBuf, TurnStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TurnStatePayload::turn,
			ByteBufCodecs.INT,     TurnStatePayload::year,
			ByteBufCodecs.BYTE,    TurnStatePayload::eraOrdinal,
			TurnStatePayload::new
	);

	@Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
