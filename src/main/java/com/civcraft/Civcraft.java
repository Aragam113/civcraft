package com.civcraft;

import com.civcraft.entity.SettlerEntity;
import com.civcraft.network.FoundTownHallPayload;
import com.civcraft.network.MoveOrderPayload;
import com.civcraft.network.NextTurnPayload;
import com.civcraft.network.SpawnSettlersPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.civcraft.registry.ModBlocks;
import com.civcraft.registry.ModEntities;
import com.civcraft.registry.ModItemGroups;
import com.civcraft.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Civcraft implements ModInitializer {
	public static final String MOD_ID = "civcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Per-unit move targets: the server steps each entity directly toward its
	 * target every tick, bypassing vanilla pathfinding entirely. This keeps the
	 * squad moving at a uniform, predictable speed in a straight line — which
	 * is exactly what an RTS march should look like.
	 */
	/** Public so client-side world renderer can draw trajectory lines. */
	public static final java.util.Map<UUID, Vec3> MOVE_TARGETS = new ConcurrentHashMap<>();
	private static final double STEP_PER_TICK = 0.12;  // ≈ 2.4 blocks/sec
	private static final double ARRIVE_RADIUS = 0.35;

	@Override
	public void onInitialize() {
		LOGGER.info("[CivCraft] Initializing server-side systems");
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		ModItemGroups.register();

		PayloadTypeRegistry.playC2S().register(MoveOrderPayload.ID, MoveOrderPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MoveOrderPayload.ID, (payload, context) -> {
			context.server().execute(() -> handleMoveOrder(context.player().level(), payload));
		});

		PayloadTypeRegistry.playC2S().register(FoundTownHallPayload.ID, FoundTownHallPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FoundTownHallPayload.ID, (payload, context) -> {
			context.server().execute(() -> handleFoundTownHall(context.player().level(), payload));
		});

		PayloadTypeRegistry.playC2S().register(SpawnSettlersPayload.ID, SpawnSettlersPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpawnSettlersPayload.ID, (payload, context) -> {
			context.server().execute(() -> handleSpawnSettlers(context.player().level(), payload));
		});

		PayloadTypeRegistry.playC2S().register(NextTurnPayload.ID, NextTurnPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(NextTurnPayload.ID, (payload, context) -> {
			context.server().execute(() -> beginTurnAdvance(context.server()));
		});

		// Freeze the day cycle + lock world to daytime on first load.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			var src = server.createCommandSourceStack().withSuppressedOutput();
			server.getCommands().performPrefixedCommand(src, "gamerule doDaylightCycle false");
			for (ServerLevel level : server.getAllLevels()) {
				level.setDayTime(6000); // noon-morning
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(Civcraft::tickMovement);
		ServerTickEvents.END_SERVER_TICK.register(Civcraft::tickTurnAdvance);

		LOGGER.info("[CivCraft] Registries loaded");
	}

	/** Offset from the town hall spire to where newly spawned settlers stand. */
	public static final int SPAWN_SOUTH_OFFSET = 4;

	private static void handleSpawnSettlers(ServerLevel level, SpawnSettlersPayload payload) {
		net.minecraft.core.BlockPos p = payload.pos();
		if (level.getBlockState(p).getBlock() != ModBlocks.TOWN_HALL) {
			LOGGER.info("[CivCraft] SpawnSettlers rejected: no town hall at {}", p);
			return;
		}
		// Anchor used in spawnSquad scatters units ~+2 east and around it in z,
		// so we offset the anchor two blocks west to center the squad on
		// (spire.x, spire.z - SPAWN_SOUTH_OFFSET).
		int sx = p.getX() - 2;
		int sz = p.getZ() - SPAWN_SOUTH_OFFSET;
		int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, sx, sz);
		net.minecraft.core.BlockPos anchor = new net.minecraft.core.BlockPos(sx, gy, sz);
		com.civcraft.item.SettlerCharterItem.spawnSquadAt(level, anchor);
		LOGGER.info("[CivCraft] Spawned settlers: spire={} anchor={}", p, anchor);
	}

	private static void handleFoundTownHall(ServerLevel level, FoundTownHallPayload payload) {
		if (payload.units().isEmpty()) return;
		double sx = 0, sz = 0;
		int count = 0;
		for (UUID u : payload.units()) {
			Entity e = level.getEntity(u);
			if (e == null) continue;
			sx += e.getX();
			sz += e.getZ();
			count++;
		}
		if (count == 0) return;
		double cx = sx / count;
		double cz = sz / count;
		int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE,
				(int) Math.floor(cx), (int) Math.floor(cz));
		net.minecraft.core.BlockPos base = new net.minecraft.core.BlockPos(
				(int) Math.floor(cx), gy, (int) Math.floor(cz));

		buildTownHall(level, base);

		// Settlers have a single-charge founding perk — consume them.
		for (UUID u : payload.units()) {
			Entity e = level.getEntity(u);
			if (e != null) e.discard();
		}
		MOVE_TARGETS.keySet().removeAll(payload.units());
		LOGGER.info("[CivCraft] Town Hall founded at {} (consumed {} units)", base, count);
	}

	/**
	 * Lays out a small 5×5 town hall: cobblestone floor, 3-high walls with
	 * glass-pane windows on every side, oak-plank roof, and a door opening
	 * facing south. Intentionally simple — vanilla blocks only, hardcoded
	 * offsets — but looks like an actual building instead of a single cube.
	 */
	private static void buildTownHall(ServerLevel level, net.minecraft.core.BlockPos base) {
		var cobble = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
		var planks = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
		var glass  = net.minecraft.world.level.block.Blocks.GLASS_PANE.defaultBlockState();
		var log    = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
		var air    = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

		// Floor (y=0) + ceiling/roof (y=4)
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				level.setBlockAndUpdate(base.offset(x, 0, z), cobble);
				level.setBlockAndUpdate(base.offset(x, 4, z), planks);
			}
		}
		// Walls
		for (int y = 1; y <= 3; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
					if (!edge) { level.setBlockAndUpdate(base.offset(x, y, z), air); continue; }
					boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
					// Glass pane at middle height, in the center of each wall (but not door).
					boolean isDoorColumn = (x == 0 && z == -2);
					boolean isWindow = y == 2 && !corner && !isDoorColumn;
					var use = corner ? log : (isWindow ? glass : cobble);
					level.setBlockAndUpdate(base.offset(x, y, z), use);
				}
			}
		}
		level.setBlockAndUpdate(base.offset(0, 1, -2), air);
		level.setBlockAndUpdate(base.offset(0, 2, -2), air);

		// Spire on top: the one block that identifies this as a CivCraft town.
		// Selection system uses it as the building marker.
		level.setBlockAndUpdate(base.offset(0, 5, 0), ModBlocks.TOWN_HALL.defaultBlockState());
	}

	private static void handleMoveOrder(ServerLevel level, MoveOrderPayload payload) {
		int n = payload.units().size();
		int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n)));
		int i = 0;
		for (UUID u : payload.units()) {
			Entity e = level.getEntity(u);
			if (!(e instanceof Mob mob)) { i++; continue; }

			int col = i % cols;
			int row = i / cols;
			double ox = (col - (cols - 1) / 2.0) * 1.5;
			double oz = (row - (cols - 1) / 2.0) * 1.5;
			double tx = payload.x() + ox;
			double tz = payload.z() + oz;
			int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE,
					(int) Math.floor(tx), (int) Math.floor(tz));

			MOVE_TARGETS.put(u, new Vec3(tx, gy, tz));
			// Keep AI off — we are fully in control of position.
			mob.setNoAi(true);
			mob.getNavigation().stop();
			i++;
		}
	}

	/** State for the "next turn" animation — a fast day→night→day sweep. */
	private static final int TURN_DURATION_TICKS = 60;
	private static int turnTicksLeft = 0;

	private static void beginTurnAdvance(net.minecraft.server.MinecraftServer server) {
		if (turnTicksLeft > 0) return; // already playing transition
		turnTicksLeft = TURN_DURATION_TICKS;
	}

	private static void tickTurnAdvance(net.minecraft.server.MinecraftServer server) {
		if (turnTicksLeft <= 0) return;
		int elapsed = TURN_DURATION_TICKS - turnTicksLeft;
		// Map elapsed ticks into a full 0..24000 minecraft-day sweep, ending at
		// morning (6000) once the counter reaches zero.
		long daytime;
		if (turnTicksLeft == 1) {
			daytime = 6000L;  // snap to morning
		} else {
			double t = (double) elapsed / TURN_DURATION_TICKS;
			daytime = 6000L + (long) (t * 24000.0);  // 6000 → 30000, %24000
		}
		long finalTime = ((daytime % 24000L) + 24000L) % 24000L;
		for (ServerLevel level : server.getAllLevels()) {
			level.setDayTime(finalTime);
		}
		turnTicksLeft--;
	}

	private static void tickMovement(net.minecraft.server.MinecraftServer server) {
		if (MOVE_TARGETS.isEmpty()) return;
		for (java.util.Iterator<java.util.Map.Entry<UUID, Vec3>> it = MOVE_TARGETS.entrySet().iterator(); it.hasNext(); ) {
			java.util.Map.Entry<UUID, Vec3> entry = it.next();
			Entity e = null;
			for (ServerLevel level : server.getAllLevels()) {
				Entity found = level.getEntity(entry.getKey());
				if (found != null) { e = found; break; }
			}
			if (e == null) { it.remove(); continue; }

			Vec3 target = entry.getValue();
			Vec3 pos = e.position();
			double dx = target.x - pos.x;
			double dz = target.z - pos.z;
			double flat = Math.hypot(dx, dz);
			if (flat < ARRIVE_RADIUS) {
				it.remove();
				continue;
			}

			double stepX = dx / flat * STEP_PER_TICK;
			double stepZ = dz / flat * STEP_PER_TICK;
			double newX = pos.x + stepX;
			double newZ = pos.z + stepZ;
			int newY = ((ServerLevel) e.level()).getHeight(
					Heightmap.Types.WORLD_SURFACE,
					(int) Math.floor(newX), (int) Math.floor(newZ));

			float yaw = (float) Math.toDegrees(Math.atan2(-stepX, stepZ));
			e.snapTo(newX, newY, newZ, yaw, 0f);
		}
	}
}
