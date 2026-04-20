package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server: "end my current turn; advance the world clock into next morning". */
public record NextTurnPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<NextTurnPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "next_turn"));

	public static final StreamCodec<ByteBuf, NextTurnPayload> CODEC =
			StreamCodec.unit(new NextTurnPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
