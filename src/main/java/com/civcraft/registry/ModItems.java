package com.civcraft.registry;

import com.civcraft.Civcraft;
import com.civcraft.item.SettlerCharterItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public final class ModItems {
	public static final Item SETTLER_CHARTER = register(
			"settler_charter",
			SettlerCharterItem::new,
			new Item.Properties().stacksTo(1)
	);

	private ModItems() {}

	public static void register() {}

	private static Item register(String name,
	                             Function<Item.Properties, Item> factory,
	                             Item.Properties properties) {
		Identifier id = Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, name);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
