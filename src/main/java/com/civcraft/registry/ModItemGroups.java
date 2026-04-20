package com.civcraft.registry;

import com.civcraft.Civcraft;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroups {
	public static final ResourceKey<CreativeModeTab> MAIN_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "main")
	);

	public static final CreativeModeTab MAIN = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			MAIN_KEY,
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.civcraft.main"))
					.icon(() -> new ItemStack(ModItems.SETTLER_CHARTER))
					.displayItems((params, entries) -> {
						entries.accept(ModItems.SETTLER_CHARTER);
						entries.accept(ModBlocks.TOWN_HALL);
					})
					.build()
	);

	private ModItemGroups() {}

	public static void register() {}
}
