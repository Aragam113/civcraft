package com.civcraft.client.building;

import com.civcraft.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the server-authoritative ghost for the local player.
 * Updated by GhostStatePayload handler. kind == -1 means "no ghost active".
 */
public final class GhostState {
	public static byte kind = -1;
	public static BlockPos pos = BlockPos.ZERO;
	public static int originX = 0;
	public static int originZ = 0;
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
			case 0 -> new int[]{-2, 2, TOWN_HALL_Y_OFFSET, 5 + TOWN_HALL_Y_OFFSET, -2, 2};  // townhall sunk 3 blocks
			case 1 -> new int[]{-1, 1, 0, 0, -1, 1};   // smithy 3x1x3
			case 2 -> new int[]{-1, 1, 0, 1, -1, 1};   // sawmill 3x2x3
			case 3 -> new int[]{-1, 1, 0, 1, -1, 1};   // storehouse 3x2x3
			case 4 -> new int[]{-1, 1, 0, 0, -1, 1};   // quarry 3x1x3
			default -> new int[]{0, 0, 0, 0, 0, 0};
		};
	}

	public record GhostBlock(int dx, int dy, int dz, BlockState state) {}

	/** Vertical offset applied to the townhall footprint so the structure is
	 *  sunk into the ground. Must match the server-side placement offset. */
	public static final int TOWN_HALL_Y_OFFSET = -3;

	// Cached shapes parsed from bundled structure NBT on first use.
	private static List<GhostBlock> cachedTownhallShape;

	/** Load blocks from a bundled .nbt at data/civcraft/structures/&lt;name&gt;.nbt. */
	private static List<GhostBlock> loadBundledShape(String name, int yOffset) {
		String path = "/data/civcraft/structures/" + name + ".nbt";
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		try (InputStream in = GhostState.class.getResourceAsStream(path)) {
			if (in == null) return null;
			CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			ListTag palette = root.getList("palette").orElse(null);
			ListTag blocks  = root.getList("blocks").orElse(null);
			if (palette == null || blocks == null) return null;
			var lookup = mc.level.holderLookup(Registries.BLOCK);
			List<BlockState> states = new ArrayList<>(palette.size());
			for (int i = 0; i < palette.size(); i++) {
				CompoundTag pt = palette.getCompoundOrEmpty(i);
				states.add(NbtUtils.readBlockState(lookup, pt));
			}
			// Structure NBT uses non-negative positions (origin at NW-bottom). Shift
			// so the center of the footprint lines up with ghost.pos.
			int sx = 0, sz = 0;
			int[] sizeArr = root.getList("size")
					.map(l -> new int[]{l.getIntOr(0, 0), l.getIntOr(1, 0), l.getIntOr(2, 0)})
					.orElse(new int[]{0, 0, 0});
			sx = sizeArr[0] / 2;
			sz = sizeArr[2] / 2;
			List<GhostBlock> out = new ArrayList<>(blocks.size());
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag b = blocks.getCompoundOrEmpty(i);
				ListTag pos = b.getList("pos").orElse(null);
				if (pos == null || pos.size() < 3) continue;
				int x = pos.getIntOr(0, 0) - sx;
				int y = pos.getIntOr(1, 0) + yOffset;
				int z = pos.getIntOr(2, 0) - sz;
				int stateIdx = b.getInt("state").orElse(0);
				if (stateIdx < 0 || stateIdx >= states.size()) continue;
				BlockState st = states.get(stateIdx);
				if (st == null || st.isAir()) continue;
				out.add(new GhostBlock(x, y, z, st));
			}
			return out;
		} catch (Exception e) {
			return null;
		}
	}

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
				// Prefer the actual bundled townhall.nbt so the preview matches
				// what the server will place. Fall back to the cobble/plank layout
				// below only if the template isn't loadable (e.g. before level init).
				if (cachedTownhallShape == null) {
					cachedTownhallShape = loadBundledShape("townhall", TOWN_HALL_Y_OFFSET);
				}
				if (cachedTownhallShape != null) {
					list.addAll(cachedTownhallShape);
					break;
				}
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						list.add(new GhostBlock(x, TOWN_HALL_Y_OFFSET + 0, z, cobble));
						list.add(new GhostBlock(x, TOWN_HALL_Y_OFFSET + 4, z, planks));
						boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
						if (!edge) continue;
						boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
						boolean doorCol = (x == 0 && z == -2);
						for (int y = 1; y <= 3; y++) {
							if (doorCol && (y == 1 || y == 2)) continue;
							BlockState use = corner ? log : (y == 2 ? glass : cobble);
							list.add(new GhostBlock(x, TOWN_HALL_Y_OFFSET + y, z, use));
						}
					}
				}
				list.add(new GhostBlock(0, TOWN_HALL_Y_OFFSET + 5, 0, ModBlocks.TOWN_HALL.defaultBlockState()));
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
			case 3 -> {
				BlockState storehouse = ModBlocks.STOREHOUSE.defaultBlockState();
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						list.add(new GhostBlock(x, 0, z, storehouse));
						if (x == 0 && z == 0) continue;
						list.add(new GhostBlock(x, 1, z, storehouse));
					}
				}
			}
			case 4 -> {
				BlockState quarry = ModBlocks.QUARRY.defaultBlockState();
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						list.add(new GhostBlock(x, 0, z, quarry));
					}
				}
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

	public static void apply(byte kind, BlockPos pos, int originX, int originZ,
	                         int progress, int target, boolean confirmed) {
		GhostState.kind = kind;
		GhostState.pos = pos;
		GhostState.originX = originX;
		GhostState.originZ = originZ;
		GhostState.progress = progress;
		GhostState.target = target;
		GhostState.confirmed = confirmed;
	}

	public static void clear() {
		kind = -1;
		dragging = false;
	}
}
