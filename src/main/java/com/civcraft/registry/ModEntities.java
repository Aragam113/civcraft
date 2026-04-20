package com.civcraft.registry;

import com.civcraft.Civcraft;
import com.civcraft.entity.SettlerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> SETTLER_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(Civcraft.MOD_ID, "settler")
	);

	public static final EntityType<SettlerEntity> SETTLER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			SETTLER_KEY,
			EntityType.Builder.of(SettlerEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.clientTrackingRange(10)
					.build(SETTLER_KEY)
	);

	private ModEntities() {}

	public static void register() {
		FabricDefaultAttributeRegistry.register(SETTLER, SettlerEntity.createAttributes());
	}
}
