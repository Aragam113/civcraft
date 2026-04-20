package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server: building at {@code pos} wants to spawn a new settler squad. */
public record SpawnSettlersPayload(BlockPos pos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpawnSettlersPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "spawn_settlers"));

	public static final StreamCodec<ByteBuf, SpawnSettlersPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SpawnSettlersPayload::pos,
			SpawnSettlersPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
