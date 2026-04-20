package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server: drag the ghost to a new world position. */
public record UpdateGhostPosPayload(int x, int y, int z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UpdateGhostPosPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "update_ghost_pos"));

	public static final StreamCodec<ByteBuf, UpdateGhostPosPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, UpdateGhostPosPayload::x,
			ByteBufCodecs.INT, UpdateGhostPosPayload::y,
			ByteBufCodecs.INT, UpdateGhostPosPayload::z,
			UpdateGhostPosPayload::new
	);

	public BlockPos pos() { return new BlockPos(x, y, z); }

	@Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
