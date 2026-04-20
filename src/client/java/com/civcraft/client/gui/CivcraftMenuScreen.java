package com.civcraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Opens when the player clicks the planet icon. Civ-style menu with a small
 * set of actions: resume (close), settings (vanilla options), main menu.
 * Pauses the integrated server while open, just like the vanilla pause menu.
 */
public class CivcraftMenuScreen extends Screen {
	public CivcraftMenuScreen() {
		super(Component.literal("CivCraft"));
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int cy = this.height / 2 - 40;
		int w = 200;

		addRenderableWidget(Button.builder(Component.literal("Продолжить"), b -> onClose())
				.bounds(cx - w / 2, cy, w, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Настройки Minecraft"), b -> {
			Minecraft mc = Minecraft.getInstance();
			mc.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(this, mc.options));
		}).bounds(cx - w / 2, cy + 28, w, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Сохранить и выйти в меню"), b -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) mc.level.disconnect(null);
			mc.disconnectWithProgressScreen();
			mc.setScreen(new TitleScreen());
		}).bounds(cx - w / 2, cy + 56, w, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(this.font, Component.literal("§6CIVCRAFT"), this.width / 2, this.height / 2 - 80, 0xFFFFFF);
		g.drawCenteredString(this.font, Component.literal("§7меню стратегии"), this.width / 2, this.height / 2 - 66, 0xAAAAAA);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(null);
	}
}
