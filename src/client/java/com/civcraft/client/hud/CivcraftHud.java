package com.civcraft.client.hud;

import com.civcraft.Civcraft;
import com.civcraft.client.camera.TopDownMode;
import com.civcraft.client.resource.ResourceState;
import com.civcraft.client.selection.SelectionState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

public final class CivcraftHud {
	private static final Identifier CURSOR        = tex("textures/gui/cursor.png");
	private static final Identifier PLANET_SHEET  = tex("textures/gui/planet.png");
	// Icons are rendered as vanilla items via GuiGraphics.renderItem — gets us
	// real multi-tone MC-style pixel art for free.
	private static final ItemStack ICON_FOUND      = new ItemStack(Items.LODESTONE);
	private static final ItemStack ICON_SPAWN      = new ItemStack(Items.VILLAGER_SPAWN_EGG);
	private static final ItemStack ICON_SMITHY     = new ItemStack(Items.ANVIL);
	private static final ItemStack ICON_SAWMILL    = new ItemStack(Items.OAK_LOG);
	private static final ItemStack ICON_STOREHOUSE = new ItemStack(Items.BARREL);
	private static final ItemStack ICON_QUARRY     = new ItemStack(Items.STONE);

	private static final ItemStack RES_FOOD       = new ItemStack(Items.BREAD);
	private static final ItemStack RES_PRODUCTION = new ItemStack(Items.IRON_PICKAXE);
	private static final ItemStack RES_GOLD       = new ItemStack(Items.GOLD_INGOT);
	private static final ItemStack RES_SCIENCE    = new ItemStack(Items.EXPERIENCE_BOTTLE);
	private static final ItemStack RES_CULTURE    = new ItemStack(Items.WRITTEN_BOOK);

	private static final int CURSOR_SIZE = 32;
	private static final int PLANET_SIZE = 40;          // small corner button now
	private static final int PLANET_SRC  = 32;
	private static final int PLANET_FRAMES = 8;

	private static final int BOTTOM_BAR_H    = 104;
	private static final int MINIMAP_W       = 160;
	private static final int MINIMAP_H       = 80;
	private static final int CMD_SLOT        = 34;
	private static final int CMD_GAP         = 4;
	private static final int CMD_COLS        = 3;
	private static final int CMD_ROWS        = 3;
	private static final int FRAME_PAD       = 6;

	private static final long ANIM_DURATION_MS = 1500L;
	private static long animStartMs = 0;

	// Minimap: rendered once to a dynamic GPU texture and blitted per frame.
	private static final Identifier MINIMAP_TEX_ID = tex("minimap_runtime");
	private static com.mojang.blaze3d.platform.NativeImage minimapImage;
	private static net.minecraft.client.renderer.texture.DynamicTexture minimapTexture;
	private static boolean minimapReady = false;
	private static int minimapBuiltW = 0, minimapBuiltH = 0;
	private static long minimapSampleStamp = 0;
	private static double minimapSampleX = 0, minimapSampleZ = 0;

	private static Identifier tex(String path) {
		return Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, path);
	}

	private CivcraftHud() {}

	public static void register() {
		HudRenderCallback.EVENT.register(CivcraftHud::render);
	}

	public static void startTurnAnimation() {
		animStartMs = System.currentTimeMillis();
	}

	/** Target "virtual" guiScale for our HUD — fixed regardless of user setting. */
	private static final double HUD_SCALE = 2.0;

	/** Divisor applied to mouse coords when hit-testing HUD elements. */
	public static double hudMouseScale(Minecraft mc) { return HUD_SCALE; }

	/** Width/height of the HUD frame in virtual pixels (raw / HUD_SCALE). */
	public static int hudWidth(Minecraft mc)  { return (int)(mc.getWindow().getScreenWidth() / HUD_SCALE); }
	public static int hudHeight(Minecraft mc) { return (int)(mc.getWindow().getScreenHeight() / HUD_SCALE); }

	private static void render(GuiGraphics graphics, DeltaTracker tracker) {
		if (!TopDownMode.active) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.mouseHandler == null || mc.getWindow() == null) return;

		// Draw HUD in "virtual" coordinates pinned to HUD_SCALE (=2). GuiGraphics
		// already maps its logical coords through guiScale, so we multiply by the
		// inverse ratio to counteract that and keep our panels at the same
		// physical pixel size regardless of the user's GUI scale.
		double guiScale = mc.getWindow().getGuiScale();
		double factor = HUD_SCALE / guiScale;
		graphics.pose().pushMatrix();
		graphics.pose().scale((float) factor, (float) factor);

		drawResourceBar(graphics, mc);
		drawPlanetButton(graphics, mc);
		drawBottomBar(graphics, mc);
		drawSelectionRect(graphics, mc);
		drawGhostButtons(graphics, mc);

		// Cursor last, over everything — already in virtual-pixel space.
		int x = (int) (mc.mouseHandler.xpos() / HUD_SCALE);
		int y = (int) (mc.mouseHandler.ypos() / HUD_SCALE);
		graphics.blit(CURSOR, x, y, CURSOR_SIZE, CURSOR_SIZE,
				0f, 0f, (float) CURSOR_SIZE, (float) CURSOR_SIZE);

		graphics.pose().popMatrix();
	}

	// ─── Top bar ──────────────────────────────────────────────────────────────

	private static void drawResourceBar(GuiGraphics g, Minecraft mc) {
		int sw = hudWidth(mc);
		ItemStack[] icons = { RES_FOOD, RES_PRODUCTION, RES_GOLD, RES_SCIENCE, RES_CULTURE };
		// Food and gold are STORED balances; production/science/culture are
		// Civ6-style per-turn YIELDS — render with a leading "+".
		String[] labels = {
				Integer.toString(ResourceState.food),
				"+" + ResourceState.production,
				Integer.toString(ResourceState.gold),
				"+" + ResourceState.science,
				"+" + ResourceState.culture,
		};
		int slotW = 72;
		int rowH = 20;
		int totalW = slotW * icons.length;
		int x0 = (sw - totalW) / 2;
		int y0 = 6;

		drawPanel(g, x0 - 4, y0 - 4, x0 + totalW + 4, y0 + rowH);

		for (int i = 0; i < icons.length; i++) {
			int ix = x0 + i * slotW;
			g.renderItem(icons[i], ix + 3, y0);
			g.drawString(mc.font, "§e" + labels[i], ix + 24, y0 + 5, 0xFFFFFFFF, false);
		}
	}

	// ─── Planet (menu) button ─────────────────────────────────────────────────

	public static int[] planetRect(Minecraft mc, double scale) {
		int sw = hudWidth(mc);
		int x0 = sw - 8 - PLANET_SIZE;
		int y0 = 8;
		return new int[]{x0, y0, x0 + PLANET_SIZE, y0 + PLANET_SIZE};
	}

	public static boolean isMouseOverPlanet(Minecraft mc) {
		int[] r = planetRect(mc, 0);
		return pointIn(mc, r);
	}

	private static void drawPlanetButton(GuiGraphics g, Minecraft mc) {
		int[] p = planetRect(mc, 0);
		boolean hover = isMouseOverPlanet(mc);
		int frame = 0;
		long now = System.currentTimeMillis();
		if (now - animStartMs < ANIM_DURATION_MS) {
			frame = (int) ((now - animStartMs) / (float) ANIM_DURATION_MS * PLANET_FRAMES) % PLANET_FRAMES;
		} else if (hover) {
			frame = (int) ((now / 120) % PLANET_FRAMES);
		}
		drawPanel(g, p[0] - 3, p[1] - 3, p[2] + 3, p[3] + 3);
		float u = frame * (float) PLANET_SRC;
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				PLANET_SHEET, p[0], p[1], u, 0f,
				PLANET_SIZE, PLANET_SIZE, PLANET_SRC, PLANET_SRC,
				PLANET_SRC * PLANET_FRAMES, PLANET_SRC);
	}

	// ─── Bottom bar (3 zones) ─────────────────────────────────────────────────

	private static void drawBottomBar(GuiGraphics g, Minecraft mc) {
		int sw = hudWidth(mc);
		int sh = hudHeight(mc);
		int y0 = sh - BOTTOM_BAR_H;
		// Base strip — dark panel with gold top border.
		g.fill(0, y0, sw, sh, 0xEE0A0604);
		g.fill(0, y0, sw, y0 + 2, 0xFFD4AF37);

		int leftW = MINIMAP_W + FRAME_PAD * 4;
		int cmdW  = CMD_COLS * (CMD_SLOT + CMD_GAP) + FRAME_PAD * 4;
		int midX0 = leftW;
		int midX1 = sw - cmdW;

		int innerY0 = y0 + FRAME_PAD + 4;
		int innerY1 = sh - FRAME_PAD;

		// Left zone: minimap
		drawPanel(g, FRAME_PAD, innerY0, leftW - FRAME_PAD, innerY1);
		drawMinimap(g, mc,
				FRAME_PAD + 4, innerY0 + 4,
				FRAME_PAD + 4 + MINIMAP_W, innerY0 + 4 + MINIMAP_H);

		// Middle zone: selection info
		drawPanel(g, midX0, innerY0, midX1, innerY1);
		drawSelectionInfo(g, mc, midX0 + 6, innerY0 + 6, midX1 - 6, innerY1 - 6);

		// Right zone: command grid
		drawPanel(g, midX1 + FRAME_PAD, innerY0, sw - FRAME_PAD, innerY1);
		drawCommandGrid(g, mc);
	}

	// ─── Minimap ──────────────────────────────────────────────────────────────

	private static void drawMinimap(GuiGraphics g, Minecraft mc, int x0, int y0, int x1, int y1) {
		int w = x1 - x0, h = y1 - y0;
		// Thin frame around the map area.
		g.fill(x0 - 1, y0 - 1, x1 + 1, y0, 0xFFD4AF37);
		g.fill(x0 - 1, y1, x1 + 1, y1 + 1, 0xFFD4AF37);
		g.fill(x0 - 1, y0, x0, y1, 0xFFD4AF37);
		g.fill(x1, y0, x1 + 1, y1, 0xFFD4AF37);

		sampleMinimap(mc, w, h);
		if (minimapReady) {
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
					MINIMAP_TEX_ID, x0, y0, 0f, 0f, w, h, w, h, w, h);
		} else {
			g.fill(x0, y0, x1, y1, 0xFF1A1A1F);
		}
		// Player dot (camera anchor center).
		int cx = x0 + w / 2, cy = y0 + h / 2;
		g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF1E1E1E);
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFD44D);
	}

	/** Resample the minimap into a GPU-uploaded dynamic texture, throttled. */
	private static void sampleMinimap(Minecraft mc, int w, int h) {
		if (mc.level == null) return;
		long now = System.currentTimeMillis();
		double dx = TopDownMode.anchorX - minimapSampleX;
		double dz = TopDownMode.anchorZ - minimapSampleZ;
		if (minimapReady && minimapBuiltW == w && minimapBuiltH == h
				&& now - minimapSampleStamp < 400 && dx * dx + dz * dz < 9) return;

		if (minimapImage == null || minimapBuiltW != w || minimapBuiltH != h) {
			if (minimapImage != null) minimapImage.close();
			minimapImage = new com.mojang.blaze3d.platform.NativeImage(
					com.mojang.blaze3d.platform.NativeImage.Format.RGBA, w, h, false);
			if (minimapTexture != null) minimapTexture.close();
			minimapTexture = new net.minecraft.client.renderer.texture.DynamicTexture(
					() -> "civcraft_minimap", minimapImage);
			mc.getTextureManager().register(MINIMAP_TEX_ID, minimapTexture);
			minimapBuiltW = w;
			minimapBuiltH = h;
		}

		int blocksAcross = 96;
		double originX = TopDownMode.anchorX - blocksAcross / 2.0;
		double originZ = TopDownMode.anchorZ - blocksAcross / 2.0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int wx = (int) (originX + (px + 0.5) * blocksAcross / w);
				int wz = (int) (originZ + (py + 0.5) * blocksAcross / h);
				int gy = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
				m.set(wx, gy - 1, wz);
				int col = 0xFF1F1A14;
				try {
					col = mc.level.getBlockState(m).getMapColor(mc.level, m).col | 0xFF000000;
				} catch (Throwable ignored) {
				}
				// NativeImage RGBA format on-disk is ABGR in memory; convert ARGB → ABGR.
				int r = (col >> 16) & 0xFF;
				int gg = (col >> 8)  & 0xFF;
				int b  =  col        & 0xFF;
				int abgr = 0xFF000000 | (b << 16) | (gg << 8) | r;
				minimapImage.setPixelABGR(px, py, abgr);
			}
		}
		minimapTexture.upload();
		minimapReady = true;
		minimapSampleStamp = now;
		minimapSampleX = TopDownMode.anchorX;
		minimapSampleZ = TopDownMode.anchorZ;
	}

	// ─── Selection info ───────────────────────────────────────────────────────

	private static void drawSelectionInfo(GuiGraphics g, Minecraft mc, int x0, int y0, int x1, int y1) {
		String title, subtitle;
		switch (SelectionState.kind) {
			case SQUAD -> {
				title = "⚔ Поселенцы";
				subtitle = "Юнитов: §e" + SelectionState.selected.size() + "§r · §7Q — основать ратушу";
			}
			case BUILDER_SQUAD -> {
				title = "⛏ Строители";
				subtitle = "Юнитов: §e" + SelectionState.selected.size() + "§r · §7Q — кузница, E — лесопилка";
			}
			case BUILDING -> {
				title = "Ратуша";
				subtitle = "§7Q — призвать поселенцев";
			}
			default -> {
				title = "§7Ничего не выделено";
				subtitle = "§8ЛКМ рамкой по юнитам, ПКМ — приказ";
			}
		}
		g.drawString(mc.font, title, x0 + 4, y0 + 6, 0xFFFFDA66, false);
		g.drawString(mc.font, subtitle, x0 + 4, y0 + 20, 0xFFE0E0E0, false);
	}

	// ─── Command grid (3×3) ───────────────────────────────────────────────────

	public static int[] perkRect(Minecraft mc, double scale, int slot) {
		int sw = hudWidth(mc);
		int sh = hudHeight(mc);
		int cmdW = CMD_COLS * (CMD_SLOT + CMD_GAP);
		int baseX = sw - FRAME_PAD - cmdW;
		int baseY = sh - BOTTOM_BAR_H + FRAME_PAD + 8;
		int col = slot % CMD_COLS;
		int row = slot / CMD_COLS;
		int x0 = baseX + col * (CMD_SLOT + CMD_GAP);
		int y0 = baseY + row * (CMD_SLOT + CMD_GAP);
		return new int[]{x0, y0, x0 + CMD_SLOT, y0 + CMD_SLOT};
	}

	public static boolean isMouseOverPerk(Minecraft mc) {
		return mousePerkSlot(mc) >= 0;
	}

	public static int mousePerkSlot(Minecraft mc) {
		ItemStack[] slots = commandSlots();
		if (slots == null) return -1;
		for (int i = 0; i < slots.length; i++) {
			if (slots[i] == null || slots[i].isEmpty()) continue;
			if (pointIn(mc, perkRect(mc, 0, i))) return i;
		}
		return -1;
	}

	/** Which item occupies which slot in the 3×3 command grid, based on selection. */
	private static ItemStack[] commandSlots() {
		ItemStack[] out = new ItemStack[CMD_COLS * CMD_ROWS];
		switch (SelectionState.kind) {
			case SQUAD         -> out[0] = ICON_FOUND;
			case BUILDING      -> out[0] = ICON_SPAWN;
			case BUILDER_SQUAD -> {
				out[0] = ICON_SMITHY;
				out[1] = ICON_SAWMILL;
				out[2] = ICON_STOREHOUSE;
				out[3] = ICON_QUARRY;
			}
			default -> { return null; }
		}
		return out;
	}

	private static void drawCommandGrid(GuiGraphics g, Minecraft mc) {
		int hover = mousePerkSlot(mc);
		ItemStack[] slots = commandSlots();
		for (int i = 0; i < CMD_COLS * CMD_ROWS; i++) {
			int[] r = perkRect(mc, 0, i);
			drawCommandSlot(g, r, slots == null ? null : slots[i], hover == i);
		}
	}

	private static void drawCommandSlot(GuiGraphics g, int[] r, ItemStack icon, boolean hover) {
		boolean empty = icon == null || icon.isEmpty();
		int bg     = empty ? 0xAA0E0905 : (hover ? 0xEE2A1F10 : 0xDD120B04);
		int border = hover ? 0xFFFFDA66 : 0xFFD4AF37;
		g.fill(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, border);
		g.fill(r[0],     r[1],     r[2],     r[3],     bg);
		if (!empty) {
			// Vanilla renderItem draws at 16×16; center it in the slot.
			int ix = r[0] + (CMD_SLOT - 16) / 2;
			int iy = r[1] + (CMD_SLOT - 16) / 2;
			g.renderItem(icon, ix, iy);
		}
	}

	// ─── Selection rectangle (drag) ───────────────────────────────────────────

	private static void drawSelectionRect(GuiGraphics g, Minecraft mc) {
		if (!SelectionState.dragging) return;
		int x0 = (int) (Math.min(SelectionState.startX, SelectionState.currentX) / HUD_SCALE);
		int y0 = (int) (Math.min(SelectionState.startY, SelectionState.currentY) / HUD_SCALE);
		int x1 = (int) (Math.max(SelectionState.startX, SelectionState.currentX) / HUD_SCALE);
		int y1 = (int) (Math.max(SelectionState.startY, SelectionState.currentY) / HUD_SCALE);
		if (x1 - x0 < 2 || y1 - y0 < 2) return;
		g.fill(x0, y0, x1, y1, 0x33D4AF37);
		g.fill(x0, y0, x1, y0 + 1, 0xFFD4AF37);
		g.fill(x0, y1 - 1, x1, y1, 0xFFD4AF37);
		g.fill(x0, y0, x0 + 1, y1, 0xFFD4AF37);
		g.fill(x1 - 1, y0, x1, y1, 0xFFD4AF37);
	}

	// ─── Ghost confirm/cancel buttons ─────────────────────────────────────────

	private static final int GHOST_BTN_SIZE = 40;
	private static final int GHOST_BTN_GAP  = 12;

	public static int[] ghostButtonRect(Minecraft mc, int idx) {
		int sw = hudWidth(mc);
		int sh = hudHeight(mc);
		int totalW = GHOST_BTN_SIZE * 2 + GHOST_BTN_GAP;
		int x0Base = (sw - totalW) / 2;
		int y0 = sh - BOTTOM_BAR_H - GHOST_BTN_SIZE - 16;
		int x0 = x0Base + idx * (GHOST_BTN_SIZE + GHOST_BTN_GAP);
		return new int[]{x0, y0, x0 + GHOST_BTN_SIZE, y0 + GHOST_BTN_SIZE};
	}

	public static int mouseGhostButton(Minecraft mc) {
		if (!com.civcraft.client.building.GhostState.isActive()
				|| com.civcraft.client.building.GhostState.confirmed) return -1;
		int mx = (int) (mc.mouseHandler.xpos() / HUD_SCALE);
		int my = (int) (mc.mouseHandler.ypos() / HUD_SCALE);
		for (int i = 0; i < 2; i++) {
			int[] r = ghostButtonRect(mc, i);
			if (mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3]) return i;
		}
		return -1;
	}

	private static void drawGhostButtons(GuiGraphics g, Minecraft mc) {
		if (!com.civcraft.client.building.GhostState.isActive()
				|| com.civcraft.client.building.GhostState.confirmed) return;
		int hover = mouseGhostButton(mc);
		drawGhostButton(g, mc, ghostButtonRect(mc, 0), true,  hover == 0);
		drawGhostButton(g, mc, ghostButtonRect(mc, 1), false, hover == 1);
	}

	private static void drawGhostButton(GuiGraphics g, Minecraft mc, int[] rect, boolean confirm, boolean hover) {
		int x0 = rect[0], y0 = rect[1], x1 = rect[2], y1 = rect[3];
		int border = confirm ? (hover ? 0xFF7AE87A : 0xFF4FA84F) : (hover ? 0xFFE87A7A : 0xFFA84F4F);
		int bg     = confirm ? (hover ? 0xCC1A3E1A : 0xCC0E2A0E) : (hover ? 0xCC3E1A1A : 0xCC2A0E0E);
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, border);
		g.fill(x0,     y0,     x1,     y1,     bg);
		int cx = (x0 + x1) / 2;
		int cy = (y0 + y1) / 2;
		int glyph = confirm ? 0xFFBFFFBF : 0xFFFFB3B3;
		if (confirm) {
			drawThickLine(g, cx - 10, cy, cx - 2, cy + 8, glyph);
			drawThickLine(g, cx - 2, cy + 8, cx + 12, cy - 8, glyph);
		} else {
			drawThickLine(g, cx - 10, cy - 10, cx + 10, cy + 10, glyph);
			drawThickLine(g, cx - 10, cy + 10, cx + 10, cy - 10, glyph);
		}
		String label = confirm ? "ПКМ" : "Q";
		int tw = mc.font.width(label);
		g.drawString(mc.font, label, x0 + (GHOST_BTN_SIZE - tw) / 2, y1 + 3, 0xFFFFFFFF, false);
	}

	// ─── Helpers ──────────────────────────────────────────────────────────────

	/** Gold-bordered dark panel — the shared frame style for HUD elements. */
	private static void drawPanel(GuiGraphics g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, 0xDD120B04);
		g.fill(x0, y0, x1, y0 + 1, 0xFFD4AF37);
		g.fill(x0, y1 - 1, x1, y1, 0xFFD4AF37);
		g.fill(x0, y0, x0 + 1, y1, 0xFFD4AF37);
		g.fill(x1 - 1, y0, x1, y1, 0xFFD4AF37);
	}

	private static boolean pointIn(Minecraft mc, int[] r) {
		int mx = (int) (mc.mouseHandler.xpos() / HUD_SCALE);
		int my = (int) (mc.mouseHandler.ypos() / HUD_SCALE);
		return mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
	}

	private static void drawThickLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
		int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
		int err = dx - dy;
		int x = x1, y = y1;
		while (true) {
			g.fill(x, y, x + 2, y + 2, color);
			if (x == x2 && y == y2) break;
			int e2 = 2 * err;
			if (e2 > -dy) { err -= dy; x += sx; }
			if (e2 < dx)  { err += dx; y += sy; }
		}
	}
}
