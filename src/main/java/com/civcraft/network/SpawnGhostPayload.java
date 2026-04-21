package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Client → server: spawn a draggable building ghost. Bound to the contributing
 * squad — settlers (townhall, instant on confirm) or builders (smithy/sawmill,
 * needs carrier delivery).
 */
public record SpawnGhostPayload(byte kind, int x, int y, int z, List<UUID> units)
		implements CustomPacketPayload {
	public static final byte KIND_TOWNHALL   = 0;
	public static final byte KIND_SMITHY     = 1;
	public static final byte KIND_SAWMILL    = 2;
	public static final byte KIND_STOREHOUSE = 3;
	public static final byte KIND_QUARRY     = 4;

	public static final CustomPacketPayload.Type<SpawnGhostPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "spawn_ghost"));

	public static final StreamCodec<ByteBuf, SpawnGhostPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, SpawnGhostPayload::kind,
			ByteBufCodecs.INT,  SpawnGhostPayload::x,
			ByteBufCodecs.INT,  SpawnGhostPayload::y,
			ByteBufCodecs.INT,  SpawnGhostPayload::z,
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), SpawnGhostPayload::units,
			SpawnGhostPayload::new
	);

	public BlockPos pos() { return new BlockPos(x, y, z); }

	@Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
