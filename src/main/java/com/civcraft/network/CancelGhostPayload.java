package com.civcraft.network;

import com.civcraft.Civcraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server: abandon the ghost. Squad stays, no resources spent yet. */
public record CancelGhostPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CancelGhostPayload> ID = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "cancel_ghost"));

	public static final StreamCodec<ByteBuf, CancelGhostPayload> CODEC =
			StreamCodec.unit(new CancelGhostPayload());

	@Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
