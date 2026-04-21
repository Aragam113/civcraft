package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: authoritative Civ-style resources for the local player.
 * Fields: food, production, gold, science, culture.
 */
public record ResourceSyncPayload(int food, int production, int gold, int science, int culture)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ResourceSyncPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "resource_sync"));

	public static final StreamCodec<ByteBuf, ResourceSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::food,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::production,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::gold,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::science,
			ByteBufCodecs.VAR_INT, ResourceSyncPayload::culture,
			ResourceSyncPayload::new
	);

	@Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
