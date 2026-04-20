package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: authoritative ghost-building state for the local player.
 * kind == 0xFF means "no active ghost" (clear it client-side).
 */
public record GhostStatePayload(byte kind, int x, int y, int z,
                                int progress, int target, boolean confirmed)
		implements CustomPacketPayload {
	public static final byte KIND_NONE = (byte) 0xFF;

	public static final CustomPacketPayload.Type<GhostStatePayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "ghost_state"));

	public static final StreamCodec<ByteBuf, GhostStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, GhostStatePayload::kind,
			ByteBufCodecs.INT,  GhostStatePayload::x,
			ByteBufCodecs.INT,  GhostStatePayload::y,
			ByteBufCodecs.INT,  GhostStatePayload::z,
			ByteBufCodecs.VAR_INT, GhostStatePayload::progress,
			ByteBufCodecs.VAR_INT, GhostStatePayload::target,
			ByteBufCodecs.BOOL, GhostStatePayload::confirmed,
			GhostStatePayload::new
	);

	public BlockPos pos() { return new BlockPos(x, y, z); }
	public static GhostStatePayload cleared() { return new GhostStatePayload(KIND_NONE, 0, 0, 0, 0, 0, false); }

	@Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
