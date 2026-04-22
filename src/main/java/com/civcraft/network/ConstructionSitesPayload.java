package com.civcraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: reservation rectangles for every building whose
 * construction is in progress (i.e. in {@code ACTIVE_BUILDS}). The
 * ghost validator uses these so a player can't drop a new building
 * on top of a half-built one.
 */
public record ConstructionSitesPayload(List<Site> sites) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ConstructionSitesPayload> ID =
			new CustomPacketPayload.Type<>(
					Identifier.fromNamespaceAndPath("civcraft", "construction_sites"));

	public record Site(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		public boolean contains(int x, int y, int z) {
			return x >= minX && x <= maxX
					&& y >= minY && y <= maxY
					&& z >= minZ && z <= maxZ;
		}
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionSitesPayload> CODEC =
			StreamCodec.of(
					(buf, payload) -> {
						buf.writeVarInt(payload.sites.size());
						for (Site s : payload.sites) {
							buf.writeVarInt(s.minX); buf.writeVarInt(s.minY); buf.writeVarInt(s.minZ);
							buf.writeVarInt(s.maxX); buf.writeVarInt(s.maxY); buf.writeVarInt(s.maxZ);
						}
					},
					buf -> {
						int n = buf.readVarInt();
						List<Site> list = new ArrayList<>(n);
						for (int i = 0; i < n; i++) {
							list.add(new Site(
									buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
									buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
						}
						return new ConstructionSitesPayload(list);
					});

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
