package com.civcraft.client.lens;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Player-centred 3D byte grid. Each cell holds {@code 1} when the block at
 * that world coord should keep its natural colour (the training set: oak
 * log, oak planks, cobblestone) and {@code 0} otherwise.
 *
 * For a first pass the grid is rebuilt fully on demand — 32×64×32 = 65536
 * getBlockState calls, which is OK for a once-per-lens-toggle or
 * once-per-second rebuild, but should be replaced with a scroll-aware
 * delta update before this ships.
 */
public final class BlockMaskGrid {
	public static final int SIZE_X = 32;
	public static final int SIZE_Y = 64;
	public static final int SIZE_Z = 32;

	private final byte[] data = new byte[SIZE_X * SIZE_Y * SIZE_Z];

	public int originX, originY, originZ;  // world coords of cell (0,0,0)

	/** 0 = air / transparent; 1 = solid but not in the keep set; 2 = keep. */
	public static final byte AIR = 0;
	public static final byte SOLID_OTHER = 1;
	public static final byte KEEP = 2;

	public void rebuild(int centerX, int centerY, int centerZ, Level level) {
		originX = centerX - SIZE_X / 2;
		originY = centerY - SIZE_Y / 2;
		originZ = centerZ - SIZE_Z / 2;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		for (int y = 0; y < SIZE_Y; y++) {
			int wy = originY + y;
			if (wy < minY || wy > maxY) {
				for (int x = 0; x < SIZE_X; x++) {
					for (int z = 0; z < SIZE_Z; z++) {
						data[idx(x, y, z)] = AIR;
					}
				}
				continue;
			}
			for (int x = 0; x < SIZE_X; x++) {
				for (int z = 0; z < SIZE_Z; z++) {
					p.set(originX + x, wy, originZ + z);
					BlockState st = level.getBlockState(p);
					var b = st.getBlock();
					byte v;
					if (b == Blocks.OAK_LOG
							|| b == Blocks.OAK_PLANKS
							|| b == Blocks.COBBLESTONE) {
						v = KEEP;
					} else if (st.isAir()) {
						v = AIR;
					} else {
						v = SOLID_OTHER;
					}
					data[idx(x, y, z)] = v;
				}
			}
		}
	}

	public byte at(int x, int y, int z) {
		return data[idx(x, y, z)];
	}

	private static int idx(int x, int y, int z) {
		return (y * SIZE_Z + z) * SIZE_X + x;
	}
}
