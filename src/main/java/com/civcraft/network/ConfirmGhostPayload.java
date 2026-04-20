package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server: commit the ghost — settlers become the building instantly,
 *  builders start carrier delivery. */
public record ConfirmGhostPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ConfirmGhostPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "confirm_ghost"));

	public static final StreamCodec<ByteBuf, ConfirmGhostPayload> CODEC =
			StreamCodec.unit(new ConfirmGhostPayload());

	@Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
