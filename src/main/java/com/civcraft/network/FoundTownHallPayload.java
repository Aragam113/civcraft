package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Client → server: "the player pressed the 'found town hall' perk with these
 * units selected". Server finds the centroid of those units and places a
 * Town Hall block there (if the ground is clear).
 */
public record FoundTownHallPayload(List<UUID> units) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FoundTownHallPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "found_town_hall"));

	public static final StreamCodec<ByteBuf, FoundTownHallPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), FoundTownHallPayload::units,
			FoundTownHallPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
