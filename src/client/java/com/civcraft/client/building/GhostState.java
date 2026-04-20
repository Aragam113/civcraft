package com.civcraft.client.building;

import com.civcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the server-authoritative ghost for the local player.
 * Updated by GhostStatePayload handler. kind == -1 means "no ghost active".
 */
public final class GhostState {
	public static byte kind = -1;
	public static BlockPos pos = BlockPos.ZERO;
	public static int progress = 0;
	public static int target = 0;
	public static boolean confirmed = false;
	public static boolean dragging = false;

	public enum Gizmo { ARROW_N, ARROW_S, ARROW_E, ARROW_W }

	private GhostState() {}

	public static boolean isActive() { return kind >= 0; }

	/** Min/max offsets relative to {@link #pos}: {minX, maxX, minY, maxY, minZ, maxZ}. */
	public static int[] bounds() {
		return switch (kind) {
			case 0 -> new int[]{-2, 2, 0, 5, -2, 2};   // townhall with spire
			case 1 -> new int[]{-1, 1, 0, 0, -1, 1};   // smithy 3x1x3
			case 2 -> new int[]{-1, 1, 0, 1, -1, 1};   // sawmill 3x2x3
			default -> new int[]{0, 0, 0, 0, 0, 0};
		};
	}

	public record GhostBlock(int dx, int dy, int dz, BlockState state) {}

	/** Block offsets (relative to {@link #pos}) and their real BlockState. Matches
	 *  what {@code Civcraft.buildTownHall / buildSmithy / buildSawmill} will place. */
	public static List<GhostBlock> shape() {
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState log    = Blocks.OAK_LOG.defaultBlockState();
		BlockState glass  = Blocks.GLASS_PANE.defaultBlockState();
		List<GhostBlock> list = new ArrayList<>();
		switch (kind) {
			case 0 -> {
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						list.add(new GhostBlock(x, 0, z, cobble));
						list.add(new GhostBlock(x, 4, z, planks));
						boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
						if (!edge) continue;
						boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
						boolean doorCol = (x == 0 && z == -2);
						for (int y = 1; y <= 3; y++) {
							if (doorCol && (y == 1 || y == 2)) continue;  // door opening
							BlockState use = corner ? log : (y == 2 ? glass : cobble);
							list.add(new GhostBlock(x, y, z, use));
						}
					}
				}
				list.add(new GhostBlock(0, 5, 0, ModBlocks.TOWN_HALL.defaultBlockState()));
			}
			case 1 -> {
				BlockState smithy = ModBlocks.SMITHY.defaultBlockState();
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						list.add(new GhostBlock(x, 0, z, smithy));
					}
				}
			}
			case 2 -> {
				BlockState sawmill = ModBlocks.SAWMILL.defaultBlockState();
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						list.add(new GhostBlock(x, 0, z, sawmill));
					}
				}
				list.add(new GhostBlock(-1, 1, -1, log));
				list.add(new GhostBlock( 1, 1, -1, log));
				list.add(new GhostBlock(-1, 1,  1, log));
				list.add(new GhostBlock( 1, 1,  1, log));
			}
		}
		return list;
	}

	/** World-space anchor for a gizmo — centered on ghost, floating at head height. */
	public static Vec3 gizmoPos(Gizmo g) {
		int[] b = bounds();
		double cx = pos.getX() + 0.5;
		double cy = pos.getY() + 1.2;
		double cz = pos.getZ() + 0.5;
		return switch (g) {
			case ARROW_N -> new Vec3(cx,                 cy, pos.getZ() + b[4] - 1.0);
			case ARROW_S -> new Vec3(cx,                 cy, pos.getZ() + b[5] + 2.0);
			case ARROW_W -> new Vec3(pos.getX() + b[0] - 1.0, cy, cz);
			case ARROW_E -> new Vec3(pos.getX() + b[1] + 2.0, cy, cz);
		};
	}

	public static void apply(byte kind, BlockPos pos, int progress, int target, boolean confirmed) {
		GhostState.kind = kind;
		GhostState.pos = pos;
		GhostState.progress = progress;
		GhostState.target = target;
		GhostState.confirmed = confirmed;
	}

	public static void clear() {
		kind = -1;
		dragging = false;
	}
}
