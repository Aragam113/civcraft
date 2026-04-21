package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server: player pressed the End Turn button. */
public record EndTurnPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EndTurnPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "end_turn"));

	public static final StreamCodec<ByteBuf, EndTurnPayload> CODEC =
			StreamCodec.unit(new EndTurnPayload());

	@Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
