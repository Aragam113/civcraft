package com.civcraft.client.hud;

import com.civcraft.Civcraft;
import com.civcraft.client.camera.TopDownMode;
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

		drawSelectionRect(graphics, mc, scale);
		drawPlanetAndPerks(graphics, mc, scale);

		// Draw the cursor last so it floats over everything.
		int x = (int) (mc.mouseHandler.xpos() / scale);
		int y = (int) (mc.mouseHandler.ypos() / scale);
		graphics.blit(CURSOR, x, y, CURSOR_SIZE, CURSOR_SIZE,
				0f, 0f, (float) CURSOR_SIZE, (float) CURSOR_SIZE);
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
		if (SelectionState.kind == SelectionState.Kind.NONE) return false;
		double scale = mc.getWindow().getGuiScale();
		int[] r = perkRect(mc, scale, 0);
		return pointIn(mc, scale, r);
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

		// If something is selected, draw a link line + the primary perk icon.
		SelectionState.Kind kind = SelectionState.kind;
		if (kind != SelectionState.Kind.NONE) {
			int[] pr = perkRect(mc, scale, 0);
			// Gold connector line from perk right edge to planet left edge.
			int y = p[1] + PLANET_SIZE / 2;
			g.fill(pr[2], y - 1, p[0], y + 1, 0xFFD4AF37);
			boolean hoverPerk = isMouseOverPerk(mc);
			drawPerkIcon(g, pr[0], pr[1], kind == SelectionState.Kind.SQUAD ? ICON_FOUND : ICON_SPAWN, hoverPerk);

			// Tooltip-style label below the icon.
			String label = kind == SelectionState.Kind.SQUAD ? "Ратуша" : "Отряд";
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
