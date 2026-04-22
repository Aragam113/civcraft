package com.civcraft.client.lens;

/**
 * Dimensions of the screen-space mask texture that the lens shader samples.
 * Kept as a holder of compile-time constants so the shader JSON hints, the
 * NativeImage allocation, and the screen-projection scaling all agree on
 * the same size.
 */
public final class MaskAtlasPacker {
	public static final int ATLAS_W = 256;
	public static final int ATLAS_H = 256;

	private MaskAtlasPacker() {}
}
