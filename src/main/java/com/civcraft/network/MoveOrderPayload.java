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
 * Client → server: "please path these selected units toward this world point".
 *
 * The server scatters the units into a grid near the target and sets each one
 * walking via its {@link net.minecraft.world.entity.ai.navigation.PathNavigation},
 * so the walk is real movement with collisions, not a teleport.
 */
public record MoveOrderPayload(List<UUID> units, double x, double y, double z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MoveOrderPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "move_order"));

	public static final StreamCodec<ByteBuf, MoveOrderPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), MoveOrderPayload::units,
			ByteBufCodecs.DOUBLE, MoveOrderPayload::x,
			ByteBufCodecs.DOUBLE, MoveOrderPayload::y,
			ByteBufCodecs.DOUBLE, MoveOrderPayload::z,
			MoveOrderPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
