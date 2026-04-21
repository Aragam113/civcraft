package com.civcraft.item;

import com.civcraft.entity.SettlerEntity;
import com.civcraft.registry.ModBlocks;
import com.civcraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class SettlerCharterItem extends Item {
	private static final String[] SQUAD_NAMES = {"⚔ Старейшина", "⚔ Поселенец", "⚔ Колонист"};
	private static final String[] BUILDER_NAMES = {"⛏ Строитель", "⛏ Строитель", "⛏ Строитель"};
	public static final String LUMBERJACK_NAME = "🪓 Лесоруб";

	/** Monotonic squad-id generator. Appended to every member's custom name so
	 *  the client can group units back into their original squad without
	 *  relying on physical proximity (which breaks when squads stand close). */
	private static final java.util.concurrent.atomic.AtomicInteger SQUAD_COUNTER =
			new java.util.concurrent.atomic.AtomicInteger(1);

	public static int nextSquadId() { return SQUAD_COUNTER.getAndIncrement(); }

	/** Encode: "⚔ Поселенец §8#N" — the trailing "#N" is what clients parse. */
	private static Component labelWithSquadId(String baseName, int squadId) {
		return Component.literal(baseName + " §8#" + squadId);
	}

	public SettlerCharterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		BlockPos placePos = ctx.getClickedPos().above();
		if (!level.isEmptyBlock(placePos)) {
			if (ctx.getPlayer() != null) {
				ctx.getPlayer().displayClientMessage(
						Component.literal("§cThe ground here is blocked."), true);
			}
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel serverLevel) {
			spawnSquadAt(serverLevel, placePos);
		}

		if (ctx.getPlayer() != null) {
			ctx.getPlayer().displayClientMessage(
					Component.literal("§6Settlers dispatched. Select them and use the Found Town Hall perk (Q)."), true);
			if (!ctx.getPlayer().hasInfiniteMaterials()) {
				ctx.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	public static void spawnSquadAt(ServerLevel world, BlockPos townHallPos) {
		double cx = townHallPos.getX() + 0.5;
		double cy = townHallPos.getY();
		double cz = townHallPos.getZ() + 0.5;
		int sid = nextSquadId();

		SettlerEntity leader = ModEntities.SETTLER.create(world, EntitySpawnReason.MOB_SUMMONED);
		if (leader != null) {
			leader.snapTo(cx + 1.5, cy, cz + 0.5, 0f, 0f);
			leader.setCustomName(labelWithSquadId("Отряд", sid));
			leader.setCustomNameVisible(false);
			leader.setPersistenceRequired();
			tagAsSquadMember(leader);
			world.addFreshEntity(leader);
		}

		for (int i = 0; i < SQUAD_NAMES.length; i++) {
			Villager v = EntityType.VILLAGER.create(world, EntitySpawnReason.MOB_SUMMONED);
			if (v == null) continue;
			double vx = cx + 1.5;
			double vz = cz + (i - 1) * 1.1;
			v.snapTo(vx, cy, vz, 0f, 0f);
			v.setCustomName(labelWithSquadId(SQUAD_NAMES[i], sid));
			v.setCustomNameVisible(true);
			v.setPersistenceRequired();
			tagAsSquadMember(v);
			world.addFreshEntity(v);
		}

		Mule mule = EntityType.MULE.create(world, EntitySpawnReason.MOB_SUMMONED);
		if (mule != null) {
			mule.snapTo(cx + 2.5, cy, cz + 0.5, 0f, 0f);
			mule.setCustomName(labelWithSquadId("⚔ Мул", sid));
			mule.setCustomNameVisible(true);
			mule.setPersistenceRequired();
			tagAsSquadMember(mule);
			world.addFreshEntity(mule);
		}
	}

	public static void spawnBuilderSquadAt(ServerLevel world, BlockPos anchor) {
		double cx = anchor.getX() + 0.5;
		double cy = anchor.getY();
		double cz = anchor.getZ() + 0.5;
		int sid = nextSquadId();
		for (int i = 0; i < BUILDER_NAMES.length; i++) {
			Villager v = EntityType.VILLAGER.create(world, EntitySpawnReason.MOB_SUMMONED);
			if (v == null) continue;
			double vx = cx + (i - 1) * 1.1;
			v.snapTo(vx, cy, cz, 0f, 0f);
			v.setCustomName(labelWithSquadId(BUILDER_NAMES[i], sid));
			v.setCustomNameVisible(true);
			v.setPersistenceRequired();
			tagAsSquadMember(v);
			world.addFreshEntity(v);
		}
	}

	public static Villager spawnLumberjackAt(ServerLevel world, BlockPos anchor) {
		Villager v = EntityType.VILLAGER.create(world, EntitySpawnReason.MOB_SUMMONED);
		if (v == null) return null;
		int sid = nextSquadId();
		v.snapTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 0f, 0f);
		v.setCustomName(labelWithSquadId(LUMBERJACK_NAME, sid));
		v.setCustomNameVisible(true);
		v.setPersistenceRequired();
		tagAsSquadMember(v);
		world.addFreshEntity(v);
		return v;
	}

	private static void tagAsSquadMember(net.minecraft.world.entity.Entity e) {
		e.addTag("civcraft_squad");
		if (e instanceof Mob mob) {
			mob.setNoAi(true);
			var speedAttr = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			if (speedAttr != null) {
				speedAttr.setBaseValue(0.28);
			}
		}
	}
}
