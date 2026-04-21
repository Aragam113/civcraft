package com.civcraft.client.gui;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Mod wiki shown in the vanilla written-book UI. Opened via the planet button. */
public class CivcraftWikiScreen extends BookViewScreen {
	private static final List<Component> PAGES = List.of(
			Component.literal("§l§6CivCraft§r\n\n§7RTS-мод в стиле\n«Козаки 3» + Civ.\n\nУправляй поселениями,\nдобывай ресурсы,\nнанимай войска."),

			Component.literal("§lКамера§r\n\n§eC§7 — вкл/выкл RTS-режим\n§eWASD§7 — панорама\n§eСКМ перетаск.§7 — panning\n§eShift+СКМ§7 — вращение\n§eКолесо§7 — zoom"),

			Component.literal("§lВыделение§r\n\n§eЛКМ-рамка§7 — выбрать отряд\n§eПКМ§7 — приказ движения\n§eQ§7 — первое действие\n§eE§7 — второе действие"),

			Component.literal("§lРесурсы§r\n\n§c▫§r Еда — мельница\n§6▫§r Дерево — лесопилка\n§7▫§r Камень — каменолом.\n§8▫§r Уголь — шахта\n§f▫§r Железо — шахта\n§e▫§r Золото — шахта"),

			Component.literal("§lПоселенцы §7⚔§r\n\nСтартовый отряд.\n\n§eQ§7: вызвать призрак\nратуши (бесплатно).\nПеретащи курсором\nили стрелками.\n§eПКМ§7 — подтвердить."),

			Component.literal("§lСтроители §7⛏§r\n\n3 заряда, 1 постройка = 1 юнит.\n\n§eQ§7: призрак кузницы (60W+40S)\n§eE§7: призрак лесопилки\n(бесплатно)."),

			Component.literal("§lЗдания§r\n\n§6Ратуша§r — центр.\n§6Кузница§r — улучшения.\n§6Лесопилка§r — 2 лесоруба,\nавтосбор дерева.\n\nСтроители охраняют\nсобственные брёвна."),

			Component.literal("§lКастомные модели§r\n\nСтавь structure_block,\nName: §ecivcraft:townhall§r\n(или smithy/sawmill).\nSave → файл в\n§f...\\generated\\civcraft\\\nstructures\\§r.\n\n/civcraft help"),

			Component.literal("§lГлобальные чертежи§r\n\nСкопируй .nbt в:\n§f.minecraft/config/\ncivcraft/blueprints/§r\n\nБудет работать во\nвсех мирах.")
	);

	public CivcraftWikiScreen() {
		super(new BookAccess(PAGES));
	}
}
