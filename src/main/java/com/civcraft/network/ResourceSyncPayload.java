package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: authoritative resource counters. The client replaces its
 * ResourceState with these values on receive — used for join-time initial
 * allocation and subsequent gains (e.g. lumberjack deposits).
 */
public record ResourceSyncPayload(int food, int wood, int stone, int coal, int iron, int gold)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ResourceSyncPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "resource_sync"));

	public static final StreamCodec<ByteBuf, ResourceSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::food,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::wood,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::stone,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::coal,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::iron,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::gold,
			ResourceSyncPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
