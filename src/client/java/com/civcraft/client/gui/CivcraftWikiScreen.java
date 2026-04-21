package com.civcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * In-game wiki, styled as an open book: table of contents on the left page,
 * selected section's rich content on the right page. Rendered over a scaled
 * copy of the vanilla book texture.
 */
public class CivcraftWikiScreen extends Screen {
	private static final Identifier BOOK_TEX = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/book.png");
	private static final int BOOK_TEX_W = 192, BOOK_TEX_H = 192;
	private static final float SCALE = 2.0f;   // book is 384×384 on-screen

	private record Section(String title, List<String> body) {}

	private static final List<Section> SECTIONS = List.of(
			new Section("§6§lОбзор", List.of(
					"§7CivCraft — RTS-мод в стиле",
					"§7Козаки 3 + Civilization.",
					"",
					"§eОсновные механики:§r",
					"§8• §7Пошаговое развитие",
					"§8• §7Экономика 6 ресурсов",
					"§8• §7Призраки и drag-placement",
					"§8• §7Отряды поселенцев и",
					"   §7строителей")),

			new Section("§6§lКамера", List.of(
					"§eC§7 — вкл/выкл RTS",
					"§eWASD§7 — панорама",
					"§eСКМ-drag§7 — панель",
					"§eShift + СКМ§7 — поворот",
					"§eКолесо мыши§7 — zoom",
					"",
					"§8Плавная float-камера с",
					"§8интерполяцией по frame.")),

			new Section("§6§lВыделение", List.of(
					"§eЛКМ рамкой§7 — выделить",
					"   §7отряд юнитов",
					"§eПКМ§7 — приказ движения",
					"",
					"§eQ§7 — 1-е действие",
					"§eE§7 — 2-е действие",
					"",
					"§8Юниты из одного отряда",
					"§8выделяются пачкой.")),

			new Section("§6§lРесурсы", List.of(
					"§cЕда§7 — мельница",
					"§6Дерево§7 — лесопилка",
					"§fКамень§7 — каменоломня",
					"§8Уголь§7 — угольная шахта",
					"§fЖелезо§7 — ж.-шахта",
					"§eЗолото§7 — золотая шахта",
					"",
					"§8Старт: 50 еды,",
					"§8100 дерева, 50 камня")),

			new Section("§6§lПоселенцы", List.of(
					"§7Стартовый отряд (4 шт).",
					"§8Один заряд — одна ратуша.",
					"",
					"§eQ§7 — призвать призрак",
					"   §7ратуши (бесплатно)",
					"",
					"§7Перетащи призрак курсором",
					"или стрелками.",
					"§eПКМ§7 — подтвердить.",
					"§eQ§7 (по призраку) — отмена.")),

			new Section("§6§lСтроители", List.of(
					"§7Отряд 3 шт, 3 заряда.",
					"§8Каждая постройка = −1 юнит.",
					"",
					"§eQ§7 — призрак кузницы",
					"   §7(60 дерева + 40 камня)",
					"§eE§7 — призрак лесопилки",
					"   §7(бесплатно)",
					"",
					"§8После постройки появится",
					"§8реальное здание.")),

			new Section("§6§lЗдания", List.of(
					"§eРатуша§7 — центр, 50+100+50",
					"§eЛесопилка§7 — 2 лесоруба,",
					"   §7авто-сбор дерева",
					"§eКузница§7 — улучшения",
					"",
					"§8Строители охраняют свои",
					"§8брёвна — лесорубы их",
					"§8не трогают.")),

			new Section("§6§lКастомизация", List.of(
					"§7Сохрани постройку через",
					"§f/give @p structure_block§7.",
					"",
					"§7Name: §ecivcraft:townhall§r",
					"§7       §ecivcraft:smithy§r",
					"§7       §ecivcraft:sawmill§r",
					"",
					"§7Save → файл в",
					"§f<world>/generated/",
					"§fcivcraft/structures/")),

			new Section("§6§lГлобал-чертежи", List.of(
					"§7Скопируй .nbt в:",
					"",
					"§f.minecraft/config/",
					"§fcivcraft/blueprints/",
					"",
					"§7Мод применит твою версию",
					"§7ВО ВСЕХ мирах.",
					"",
					"§8/civcraft help — путь"))
	);

	private int selected = 0;

	public CivcraftWikiScreen() {
		super(Component.literal("CivCraft Wiki"));
	}

	@Override
	protected void init() {
		super.init();
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(g, mouseX, mouseY, partialTick);

		int bookW = (int) (BOOK_TEX_W * SCALE);
		int bookH = (int) (BOOK_TEX_H * SCALE);
		int bookX = (this.width  - bookW) / 2;
		int bookY = (this.height - bookH) / 2;

		// Vanilla book texture at 2× scale.
		g.blit(RenderPipelines.GUI_TEXTURED, BOOK_TEX,
				bookX, bookY, 0f, 0f,
				bookW, bookH, BOOK_TEX_W, BOOK_TEX_H, BOOK_TEX_W, BOOK_TEX_H);

		// Left page: table of contents (clickable).
		int leftX  = bookX + 30;
		int leftY  = bookY + 28;
		int leftW  = 150;

		g.drawString(this.font, "§l§0Оглавление", leftX, leftY, 0, false);
		int y = leftY + 16;
		for (int i = 0; i < SECTIONS.size(); i++) {
			String title = SECTIONS.get(i).title();
			boolean hover = mouseX >= leftX && mouseX <= leftX + leftW
					&& mouseY >= y - 1 && mouseY <= y + 10;
			boolean active = i == selected;
			String prefix = active ? "§l§4▶ " : (hover ? "§l§1▶ " : "§l§8  ");
			g.drawString(this.font, prefix + stripColors(title), leftX, y, 0, false);
			y += 12;
		}

		// Right page: section content.
		int rightX = bookX + bookW / 2 + 10;
		int rightY = bookY + 28;
		Section s = SECTIONS.get(selected);
		g.drawString(this.font, s.title(), rightX, rightY, 0, false);
		int cy = rightY + 18;
		for (String line : s.body()) {
			List<FormattedCharSequence> wrapped = this.font.split(
					Component.literal(line), 150);
			for (FormattedCharSequence fcs : wrapped) {
				g.drawString(this.font, fcs, rightX, cy, 0, false);
				cy += this.font.lineHeight + 1;
			}
		}

		super.render(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClicked) {
		double mouseX = event.x();
		double mouseY = event.y();
		int bookW = (int) (BOOK_TEX_W * SCALE);
		int bookH = (int) (BOOK_TEX_H * SCALE);
		int bookX = (this.width  - bookW) / 2;
		int bookY = (this.height - bookH) / 2;
		int leftX = bookX + 30;
		int leftY = bookY + 28 + 16;
		int leftW = 150;
		for (int i = 0; i < SECTIONS.size(); i++) {
			int y = leftY + i * 12;
			if (mouseX >= leftX && mouseX <= leftX + leftW
					&& mouseY >= y - 1 && mouseY <= y + 10) {
				selected = i;
				if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
					this.minecraft.getSoundManager().play(
							net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
									net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClicked);
	}

	private static String stripColors(String s) {
		return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
	}
}
