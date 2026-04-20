package com.civcraft.client.hud;

import com.civcraft.Civcraft;
import com.civcraft.client.camera.TopDownMode;
import com.civcraft.client.resource.ResourceState;
import com.civcraft.client.selection.SelectionState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class CivcraftHud {
	private static final Identifier CURSOR        = tex("textures/gui/cursor.png");
	private static final Identifier PLANET_SHEET  = tex("textures/gui/planet.png");
	private static final Identifier ICON_FOUND    = tex("textures/gui/icon_town_hall.png");
	private static final Identifier ICON_SPAWN    = tex("textures/gui/icon_spawn_settlers.png");
	private static final Identifier ICON_SMITHY   = tex("textures/gui/icon_smithy.png");
	private static final Identifier ICON_SAWMILL  = tex("textures/gui/icon_sawmill.png");

	private static final Identifier RES_FOOD  = tex("textures/gui/res_food.png");
	private static final Identifier RES_WOOD  = tex("textures/gui/res_wood.png");
	private static final Identifier RES_STONE = tex("textures/gui/res_stone.png");
	private static final Identifier RES_COAL  = tex("textures/gui/res_coal.png");
	private static final Identifier RES_IRON  = tex("textures/gui/res_iron.png");
	private static final Identifier RES_GOLD  = tex("textures/gui/res_gold.png");

	private static final int CURSOR_SIZE = 32;
	private static final int PLANET_SIZE = 64;          // rendered size (2× source)
	private static final int PLANET_SRC  = 32;          // per-frame source size
	private static final int PLANET_FRAMES = 8;

	private static final int ICON_SIZE = 32;
	private static final int MARGIN    = 14;
	private static final int PERK_GAP  = 12;

	private static final long ANIM_DURATION_MS = 1500L;
	private static long animStartMs = 0;

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

	private static void render(GuiGraphics graphics, DeltaTracker tracker) {
		if (!TopDownMode.active) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.mouseHandler == null || mc.getWindow() == null) return;

		double scale = mc.getWindow().getGuiScale();

		drawResourceBar(graphics, mc);
		drawSelectionRect(graphics, mc, scale);
		drawPlanetAndPerks(graphics, mc, scale);
		drawGhostButtons(graphics, mc);

		// Draw the cursor last so it floats over everything.
		int x = (int) (mc.mouseHandler.xpos() / scale);
		int y = (int) (mc.mouseHandler.ypos() / scale);
		graphics.blit(CURSOR, x, y, CURSOR_SIZE, CURSOR_SIZE,
				0f, 0f, (float) CURSOR_SIZE, (float) CURSOR_SIZE);
	}

	private static void drawResourceBar(GuiGraphics g, Minecraft mc) {
		int sw = mc.getWindow().getGuiScaledWidth();
		Identifier[] icons = { RES_FOOD, RES_WOOD, RES_STONE, RES_COAL, RES_IRON, RES_GOLD };
		int[] values = {
				ResourceState.food, ResourceState.wood, ResourceState.stone,
				ResourceState.coal, ResourceState.iron, ResourceState.gold,
		};
		int iconSize = 18;
		int slotW = 72;
		int rowH = iconSize + 4;
		int totalW = slotW * icons.length;
		int x0 = (sw - totalW) / 2;
		int y0 = 6;

		g.fill(x0 - 4, y0 - 4, x0 + totalW + 4, y0 + rowH, 0xCC120B04);
		g.fill(x0 - 4, y0 - 4, x0 + totalW + 4, y0 - 3, 0xFFD4AF37);
		g.fill(x0 - 4, y0 + rowH - 1, x0 + totalW + 4, y0 + rowH, 0xFFD4AF37);
		g.fill(x0 - 4, y0 - 4, x0 - 3, y0 + rowH, 0xFFD4AF37);
		g.fill(x0 + totalW + 3, y0 - 4, x0 + totalW + 4, y0 + rowH, 0xFFD4AF37);

		for (int i = 0; i < icons.length; i++) {
			int ix = x0 + i * slotW;
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
					icons[i], ix + 3, y0 + 1, 0f, 0f,
					iconSize, iconSize, iconSize, iconSize, iconSize, iconSize);
			g.drawString(mc.font, "§e" + values[i], ix + 24, y0 + 6, 0xFFFFFFFF, false);
		}
	}

	private static void drawSelectionRect(GuiGraphics g, Minecraft mc, double scale) {
		if (!SelectionState.dragging) return;
		int x0 = (int) (Math.min(SelectionState.startX, SelectionState.currentX) / scale);
		int y0 = (int) (Math.min(SelectionState.startY, SelectionState.currentY) / scale);
		int x1 = (int) (Math.max(SelectionState.startX, SelectionState.currentX) / scale);
		int y1 = (int) (Math.max(SelectionState.startY, SelectionState.currentY) / scale);
		if (x1 - x0 < 2 || y1 - y0 < 2) return;
		// Translucent gold fill + solid gold frame — matches the planet/perk theme.
		g.fill(x0, y0, x1, y1, 0x33D4AF37);
		g.fill(x0, y0, x1, y0 + 1, 0xFFD4AF37);
		g.fill(x0, y1 - 1, x1, y1, 0xFFD4AF37);
		g.fill(x0, y0, x0 + 1, y1, 0xFFD4AF37);
		g.fill(x1 - 1, y0, x1, y1, 0xFFD4AF37);
	}

	// --- layout ---------------------------------------------------------------

	public static int[] planetRect(Minecraft mc, double scale) {
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int x0 = sw - MARGIN - PLANET_SIZE;
		int y0 = sh - MARGIN - PLANET_SIZE;
		return new int[]{x0, y0, x0 + PLANET_SIZE, y0 + PLANET_SIZE};
	}

	public static int[] perkRect(Minecraft mc, double scale, int slot) {
		int[] p = planetRect(mc, scale);
		// Perks expand to the LEFT of the planet, RTL order.
		int x1 = p[0] - 16 - slot * (ICON_SIZE + PERK_GAP);
		int x0 = x1 - ICON_SIZE;
		int y0 = p[1] + (PLANET_SIZE - ICON_SIZE) / 2;
		return new int[]{x0, y0, x0 + ICON_SIZE, y0 + ICON_SIZE};
	}

	public static boolean isMouseOverPlanet(Minecraft mc) {
		double scale = mc.getWindow().getGuiScale();
		int[] r = planetRect(mc, scale);
		return pointIn(mc, scale, r);
	}

	public static boolean isMouseOverPerk(Minecraft mc) {
		return mousePerkSlot(mc) >= 0;
	}

	private static final int GHOST_BTN_SIZE = 40;
	private static final int GHOST_BTN_GAP  = 12;
	private static final int GHOST_BTN_Y_FROM_BOTTOM = 60;

	/** Returns [x0, y0, x1, y1] for the given ghost button (0=confirm, 1=cancel). */
	public static int[] ghostButtonRect(Minecraft mc, int idx) {
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int totalW = GHOST_BTN_SIZE * 2 + GHOST_BTN_GAP;
		int x0Base = (sw - totalW) / 2;
		int y0 = sh - GHOST_BTN_Y_FROM_BOTTOM;
		int x0 = x0Base + idx * (GHOST_BTN_SIZE + GHOST_BTN_GAP);
		return new int[]{x0, y0, x0 + GHOST_BTN_SIZE, y0 + GHOST_BTN_SIZE};
	}

	/** -1 if not over either, 0 = confirm, 1 = cancel. */
	public static int mouseGhostButton(Minecraft mc) {
		if (!com.civcraft.client.building.GhostState.isActive()
				|| com.civcraft.client.building.GhostState.confirmed) return -1;
		double scale = mc.getWindow().getGuiScale();
		int mx = (int) (mc.mouseHandler.xpos() / scale);
		int my = (int) (mc.mouseHandler.ypos() / scale);
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
		int border, bg;
		if (confirm) {
			border = hover ? 0xFF7AE87A : 0xFF4FA84F;
			bg     = hover ? 0xCC1A3E1A : 0xCC0E2A0E;
		} else {
			border = hover ? 0xFFE87A7A : 0xFFA84F4F;
			bg     = hover ? 0xCC3E1A1A : 0xCC2A0E0E;
		}
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, border);
		g.fill(x0,     y0,     x1,     y1,     bg);
		// Glyph: ✓ or ✗ via lines.
		int cx = (x0 + x1) / 2;
		int cy = (y0 + y1) / 2;
		int glyph = confirm ? 0xFFBFFFBF : 0xFFFFB3B3;
		if (confirm) {
			// Left stroke (short): from (cx-10, cy) to (cx-2, cy+8)
			drawThickLine(g, cx - 10, cy, cx - 2, cy + 8, glyph);
			// Right stroke (long): from (cx-2, cy+8) to (cx+12, cy-8)
			drawThickLine(g, cx - 2, cy + 8, cx + 12, cy - 8, glyph);
		} else {
			drawThickLine(g, cx - 10, cy - 10, cx + 10, cy + 10, glyph);
			drawThickLine(g, cx - 10, cy + 10, cx + 10, cy - 10, glyph);
		}
		String label = confirm ? "ПКМ" : "Q";
		int tw = mc.font.width(label);
		g.drawString(mc.font, label, x0 + (GHOST_BTN_SIZE - tw) / 2, y1 + 3, 0xFFFFFFFF, false);
	}

	/** 2-pixel-thick line between two points, using axis-aligned fills for speed. */
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

	/** -1 if mouse is not over any perk slot, else the slot index (0-based). */
	public static int mousePerkSlot(Minecraft mc) {
		if (SelectionState.kind == SelectionState.Kind.NONE) return -1;
		int slots = SelectionState.kind == SelectionState.Kind.BUILDER_SQUAD ? 2 : 1;
		double scale = mc.getWindow().getGuiScale();
		for (int i = 0; i < slots; i++) {
			if (pointIn(mc, scale, perkRect(mc, scale, i))) return i;
		}
		return -1;
	}

	private static boolean pointIn(Minecraft mc, double scale, int[] r) {
		int mx = (int) (mc.mouseHandler.xpos() / scale);
		int my = (int) (mc.mouseHandler.ypos() / scale);
		return mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
	}

	// --- drawing --------------------------------------------------------------

	private static void drawPlanetAndPerks(GuiGraphics g, Minecraft mc, double scale) {
		int[] p = planetRect(mc, scale);
		boolean hoverPlanet = isMouseOverPlanet(mc);

		// Animated planet frame.
		int frame = 0;
		long now = System.currentTimeMillis();
		if (now - animStartMs < ANIM_DURATION_MS) {
			float t = (now - animStartMs) / (float) ANIM_DURATION_MS;
			frame = (int) (t * PLANET_FRAMES) % PLANET_FRAMES;
		} else if (hoverPlanet) {
			frame = (int) ((now / 120) % PLANET_FRAMES);  // idle bob on hover
		}

		drawPlanetSprite(g, p[0], p[1], frame, hoverPlanet);

		// If something is selected, draw link line + perk icons for that kind.
		SelectionState.Kind kind = SelectionState.kind;
		if (kind == SelectionState.Kind.NONE) return;
		Identifier[] icons;
		String[] labels;
		switch (kind) {
			case SQUAD         -> { icons = new Identifier[]{ICON_FOUND};              labels = new String[]{"Ратуша"}; }
			case BUILDING      -> { icons = new Identifier[]{ICON_SPAWN};              labels = new String[]{"Отряд"}; }
			case BUILDER_SQUAD -> { icons = new Identifier[]{ICON_SMITHY, ICON_SAWMILL}; labels = new String[]{"Кузница", "Лесопилка"}; }
			default            -> { return; }
		}
		int hoverSlot = mousePerkSlot(mc);
		for (int i = 0; i < icons.length; i++) {
			int[] pr = perkRect(mc, scale, i);
			if (i == 0) {
				int y = p[1] + PLANET_SIZE / 2;
				g.fill(pr[2], y - 1, p[0], y + 1, 0xFFD4AF37);
			}
			drawPerkIcon(g, pr[0], pr[1], icons[i], hoverSlot == i);
			String label = labels[i];
			int tw = mc.font.width(label);
			int tx = pr[0] + (ICON_SIZE - tw) / 2;
			g.drawString(mc.font, "§e" + label, tx, pr[3] + 4, 0xFFFFFFFF, false);
		}
	}

	private static void drawPlanetSprite(GuiGraphics g, int x0, int y0, int frame, boolean hover) {
		// Gold ring around planet (two concentric "rings" drawn as thin fills).
		int cx = x0 + PLANET_SIZE / 2;
		int cy = y0 + PLANET_SIZE / 2;
		int rOuter = PLANET_SIZE / 2 + 3;
		int rInner = PLANET_SIZE / 2 + 1;
		int ringColor = hover ? 0xFFFFDA66 : 0xFFD4AF37;
		ringCircle(g, cx, cy, rOuter, ringColor);
		ringCircle(g, cx, cy, rInner, ringColor);

		// Planet sprite frame — blit from 256×32 sheet, scaled to 64×64.
		float u = frame * (float) PLANET_SRC;
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				PLANET_SHEET,
				x0, y0,
				u, 0f,
				PLANET_SIZE, PLANET_SIZE,
				PLANET_SRC, PLANET_SRC,
				PLANET_SRC * PLANET_FRAMES, PLANET_SRC);
	}

	private static void drawPerkIcon(GuiGraphics g, int x0, int y0, Identifier icon, boolean hover) {
		int border = hover ? 0xFFFFDA66 : 0xFFD4AF37;
		int bg     = hover ? 0xEE2A1F10 : 0xDD120B04;
		int x1 = x0 + ICON_SIZE, y1 = y0 + ICON_SIZE;
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, border);
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, bg);
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
				icon, x0, y0, 0f, 0f,
				ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
	}

	/** Draw a 1-px-thick circle outline via the midpoint algorithm. */
	private static void ringCircle(GuiGraphics g, int cx, int cy, int r, int color) {
		int x = r, y = 0, err = 0;
		while (x >= y) {
			g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
			g.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
			g.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
			g.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
			g.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
			g.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);
			g.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
			g.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
			y++;
			if (err <= 0) err += 2 * y + 1;
			if (err > 0)  { x--; err -= 2 * x + 1; }
		}
	}
}
