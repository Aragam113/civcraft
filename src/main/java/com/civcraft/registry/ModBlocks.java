package com.civcraft.registry;

import com.civcraft.Civcraft;
import com.civcraft.block.TownHallBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

public final class ModBlocks {
	public static final Block TOWN_HALL = register(
			"town_hall",
			TownHallBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(5.0f, 6.0f)
	);
	public static final Block SMITHY = register(
			"smithy",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 6.0f)
	);
	public static final Block SAWMILL = register(
			"sawmill",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.0f, 4.0f)
	);

	private ModBlocks() {}

	public static void register() {
		// Class initialization triggers registry calls below.
	}

	private static Block register(String name,
	                              Function<BlockBehaviour.Properties, Block> factory,
	                              BlockBehaviour.Properties properties) {
		Identifier id = Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		Block block = factory.apply(properties.setId(blockKey));
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		BlockItem blockItem = new BlockItem(
				block,
				new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)
		);
		Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);

		return block;
	}
}
