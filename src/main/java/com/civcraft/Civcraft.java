package com.civcraft;

import com.civcraft.building.GhostBuilding;
import com.civcraft.network.CancelGhostPayload;
import com.civcraft.network.ConfirmGhostPayload;
import com.civcraft.network.GhostStatePayload;
import com.civcraft.network.MoveOrderPayload;
import com.civcraft.network.ResourceSyncPayload;
import com.civcraft.network.SpawnGhostPayload;
import com.civcraft.network.SpawnSettlersPayload;
import com.civcraft.network.UpdateGhostPosPayload;
import com.civcraft.registry.ModBlocks;
import com.civcraft.registry.ModEntities;
import com.civcraft.registry.ModItemGroups;
import com.civcraft.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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

	/** Per-unit move targets for the real-time march system. */
	public static final java.util.Map<UUID, Vec3> MOVE_TARGETS = new ConcurrentHashMap<>();
	private static final double STEP_PER_TICK = 0.12;
	private static final double ARRIVE_RADIUS = 0.35;

	/** Offset from the town hall spire to where newly spawned settlers stand. */
	public static final int SPAWN_SOUTH_OFFSET = 4;

	/** Per-lumberjack state: home sawmill position + current phase. */
	public enum WorkPhase { TO_LOG, CHOP, TO_HOME, DEPOSIT }
	public static final class LumberjackJob {
		public BlockPos home;
		public WorkPhase phase = WorkPhase.TO_LOG;
		public BlockPos target;  // log pos or sawmill pos
		public int chopTicks = 0;
		public UUID owner;
		public LumberjackJob(BlockPos home, UUID owner) { this.home = home; this.owner = owner; }
	}
	public static final java.util.Map<UUID, LumberjackJob> LUMBERJACKS = new ConcurrentHashMap<>();
	public static final java.util.Map<UUID, int[]> PLAYER_RESOURCES = new ConcurrentHashMap<>();
	/** Log blocks placed as part of player buildings — lumberjacks must skip these. */
	public static final java.util.Set<BlockPos> PROTECTED_LOGS = ConcurrentHashMap.newKeySet();

	/** One PENDING (unconfirmed, draggable) ghost per player. Blocks the client UI. */
	public static final java.util.Map<UUID, GhostBuilding> PENDING_GHOSTS = new ConcurrentHashMap<>();

	/** Confirmed builds currently under construction — multiple allowed per player. */
	public static final java.util.Set<GhostBuilding> ACTIVE_BUILDS = ConcurrentHashMap.newKeySet();

	public enum CarrierPhase { TO_HALL, WAIT_HALL, TO_GHOST, WAIT_GHOST }
	public static final class CarrierJob {
		public final GhostBuilding target;
		public CarrierPhase phase = CarrierPhase.TO_HALL;
		public int waitTicks = 0;
		public CarrierJob(GhostBuilding target) { this.target = target; }
	}
	public static final java.util.Map<UUID, CarrierJob> CARRIERS = new ConcurrentHashMap<>();
	private static final int CARRIER_WAIT_TICKS = 10;
	private static final int DELIVERY_TRIPS = 9;  // 3 builders × 3 trips = smithy/sawmill done
	public static final int GHOST_DRAG_RADIUS = 5;  // blocks the ghost can be moved from its origin
	private static final double LUMBERJACK_STEP = 0.08;
	private static final int LUMBERJACK_SEARCH_RADIUS = 48;
	private static final int CHOP_DURATION_TICKS = 40;
	private static final int PRODUCTION_PER_CHOP = 5;

	@Override
	public void onInitialize() {
		LOGGER.info("[CivCraft] Initializing server-side systems");
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		ModItemGroups.register();

		PayloadTypeRegistry.playC2S().register(MoveOrderPayload.ID, MoveOrderPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MoveOrderPayload.ID, (payload, context) ->
				context.server().execute(() -> handleMoveOrder(context.player().level(), payload)));

		PayloadTypeRegistry.playC2S().register(SpawnSettlersPayload.ID, SpawnSettlersPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpawnSettlersPayload.ID, (payload, context) ->
				context.server().execute(() -> handleSpawnSettlers(context.player().level(), payload)));

		PayloadTypeRegistry.playC2S().register(SpawnGhostPayload.ID, SpawnGhostPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpawnGhostPayload.ID, (payload, context) ->
				context.server().execute(() -> handleSpawnGhost(context.player(), payload)));

		PayloadTypeRegistry.playC2S().register(UpdateGhostPosPayload.ID, UpdateGhostPosPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UpdateGhostPosPayload.ID, (payload, context) ->
				context.server().execute(() -> handleUpdateGhostPos(context.player(), payload)));

		PayloadTypeRegistry.playC2S().register(ConfirmGhostPayload.ID, ConfirmGhostPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ConfirmGhostPayload.ID, (payload, context) ->
				context.server().execute(() -> handleConfirmGhost(context.player())));

		PayloadTypeRegistry.playC2S().register(CancelGhostPayload.ID, CancelGhostPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CancelGhostPayload.ID, (payload, context) ->
				context.server().execute(() -> handleCancelGhost(context.player())));

		PayloadTypeRegistry.playS2C().register(ResourceSyncPayload.ID, ResourceSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GhostStatePayload.ID, GhostStatePayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> handlePlayerJoin(handler.getPlayer())));

		ServerTickEvents.END_SERVER_TICK.register(Civcraft::tickMovement);
		ServerTickEvents.END_SERVER_TICK.register(Civcraft::tickLumberjacks);
		ServerTickEvents.END_SERVER_TICK.register(Civcraft::tickCarriers);

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
				dispatcher.register(Commands.literal("civcraft")
						.then(Commands.literal("help").executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayer();
							if (p != null) {
								p.sendSystemMessage(Component.literal(
										"§6CivCraft: свои постройки §r\n" +
										"§71. /give @p structure_block\n" +
										"§72. В блоке — Save Mode, Name: §ecivcraft:townhall§7 (или §ecivcraft:smithy§7, §ecivcraft:sawmill§7)\n" +
										"§73. Save → файл сохранится в §f<world>/generated/civcraft/structures/§7\n" +
										"§74. Чтобы работало во ВСЕХ мирах сразу, скопируй .nbt в:\n" +
										"§f   " + globalBlueprintDir().toAbsolutePath()));
							}
							return 1;
						}))));

		ensureBlueprintDir();

		LOGGER.info("[CivCraft] Registries loaded");
	}

	/**
	 * Try to place a player-saved structure template (vanilla structure-block
	 * format) at {@code base}. Returns true if a template was found and placed.
	 * Logs within the placed volume are added to PROTECTED_LOGS so lumberjacks
	 * won't chop them.
	 */
	private static boolean placeFromTemplate(ServerLevel level, BlockPos base, String name) {
		StructureTemplate tpl = loadGlobalBlueprint(level, name);
		String loadedFrom = "global";
		if (tpl == null) {
			tpl = loadBundledBlueprint(level, name);
			loadedFrom = "bundled";
		}
		if (tpl == null) {
			Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
			var opt = level.getStructureManager().get(id);
			if (opt.isEmpty()) return false;
			tpl = opt.get();
			loadedFrom = "per-world";
		}
		LOGGER.info("[CivCraft] Placed template {} from {}", name, loadedFrom);
		// Structure NBT stores blocks with non-negative offsets (origin = NW-bottom
		// corner). Shift the placement so the template's CENTER lands on the
		// caller's base — matches the client-side ghost preview which is also
		// centered around the drag position.
		net.minecraft.core.Vec3i size = tpl.getSize();
		BlockPos anchor = base.offset(-size.getX() / 2, 0, -size.getZ() / 2);
		StructurePlaceSettings settings = new StructurePlaceSettings();
		// Skip air + structure-block markers from the template so existing world
		// blocks (grass, dirt, etc.) show through where the template has voids.
		settings.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		tpl.placeInWorld(level, anchor, anchor, settings, level.getRandom(), 2);
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					BlockPos p = anchor.offset(x, y, z);
					if (isLog(level, p)) PROTECTED_LOGS.add(p.immutable());
				}
			}
		}
		return true;
	}

	/** Global per-installation blueprint directory: {@code config/civcraft/blueprints/}. */
	public static java.nio.file.Path globalBlueprintDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("blueprints");
	}

	/** Load a template bundled inside the mod jar at data/civcraft/structures/&lt;name&gt;.nbt. */
	private static StructureTemplate loadBundledBlueprint(ServerLevel level, String name) {
		String path = "/data/" + MOD_ID + "/structures/" + name + ".nbt";
		try (java.io.InputStream in = Civcraft.class.getResourceAsStream(path)) {
			if (in == null) return null;
			net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(
					in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			StructureTemplate tpl = new StructureTemplate();
			tpl.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
			return tpl;
		} catch (Exception e) {
			LOGGER.warn("[CivCraft] Failed to load bundled blueprint {}: {}", name, e.getMessage());
			return null;
		}
	}

	private static StructureTemplate loadGlobalBlueprint(ServerLevel level, String name) {
		java.nio.file.Path path = globalBlueprintDir().resolve(name + ".nbt");
		if (!java.nio.file.Files.exists(path)) return null;
		try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
			net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(
					in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			StructureTemplate tpl = new StructureTemplate();
			tpl.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
			return tpl;
		} catch (Exception e) {
			LOGGER.warn("[CivCraft] Failed to load blueprint {}: {}", path, e.getMessage());
			return null;
		}
	}

	private static void ensureBlueprintDir() {
		try {
			java.nio.file.Path dir = globalBlueprintDir();
			if (!java.nio.file.Files.exists(dir)) {
				java.nio.file.Files.createDirectories(dir);
				java.nio.file.Path readme = dir.resolve("README.txt");
				java.nio.file.Files.writeString(readme,
						"Drop vanilla structure-block .nbt saves here:\n" +
						"  townhall.nbt  smithy.nbt  sawmill.nbt\n" +
						"They override the built-in buildings across every world.\n");
			}
		} catch (Exception e) {
			LOGGER.warn("[CivCraft] Could not prepare blueprint dir: {}", e.getMessage());
		}
	}

	private static final String STARTER_TAG = "civcraft_starter_spawned";

	private static void handlePlayerJoin(ServerPlayer player) {
		ServerLevel level = player.level() instanceof ServerLevel sl ? sl : null;
		if (level == null) return;
		// Resources are always pushed on join so the HUD shows the correct
		// totals, but they were also persisted across sessions via PLAYER_RESOURCES.
		// TODO: load saved counts from player NBT instead of resetting.
		int[] starting = PLAYER_RESOURCES.computeIfAbsent(player.getUUID(),
				u -> new int[]{30, 40, 10, 0, 0});
		sendResources(player, starting);

		// Only spawn the starter settler squad the FIRST time a player enters
		// this world — the tag persists in the player's NBT, so re-joining no
		// longer duplicates units.
		if (player.getTags().contains(STARTER_TAG)) {
			LOGGER.info("[CivCraft] {} rejoined (starter already spawned)", player.getName().getString());
			return;
		}
		player.addTag(STARTER_TAG);

		BlockPos at = player.blockPosition();
		BlockPos settler = findClearGround(level, at.offset(-4, 0, 0));
		com.civcraft.item.SettlerCharterItem.spawnSquadAt(level, settler);
		LOGGER.info("[CivCraft] Starter settlers spawned for {} near {}", player.getName().getString(), at);
	}

	private static BlockPos findClearGround(ServerLevel level, BlockPos near) {
		int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, near.getX(), near.getZ());
		return new BlockPos(near.getX(), gy, near.getZ());
	}

	private static void sendResources(ServerPlayer player, int[] r) {
		ServerPlayNetworking.send(player,
				new ResourceSyncPayload(r[0], r[1], r[2], r[3], r[4]));
	}

	private static void handleSpawnGhost(ServerPlayer player, SpawnGhostPayload p) {
		if (!(player.level() instanceof ServerLevel level)) return;
		if (PENDING_GHOSTS.containsKey(player.getUUID())) {
			player.displayClientMessage(Component.literal("§cУ тебя уже есть активный призрак. Подтверди или отмени его."), true);
			return;
		}
		BlockPos p0 = groundAt(level, p.pos());
		int target = (p.kind() == SpawnGhostPayload.KIND_TOWNHALL) ? 0 : DELIVERY_TRIPS;
		GhostBuilding g = new GhostBuilding(player.getUUID(), p.kind(), p0, target, p.units());
		PENDING_GHOSTS.put(player.getUUID(), g);
		sendGhostState(player, g);
	}

	private static void handleUpdateGhostPos(ServerPlayer player, UpdateGhostPosPayload p) {
		GhostBuilding g = PENDING_GHOSTS.get(player.getUUID());
		if (g == null || g.confirmed) return;
		if (!(player.level() instanceof ServerLevel level)) return;
		// Clamp the drag target to within GHOST_DRAG_RADIUS of the spawn origin.
		int dx = p.x() - g.originX;
		int dz = p.z() - g.originZ;
		double d2 = dx * dx + dz * dz;
		int nx = p.x();
		int nz = p.z();
		if (d2 > (double) GHOST_DRAG_RADIUS * GHOST_DRAG_RADIUS) {
			double d = Math.sqrt(d2);
			nx = g.originX + (int) Math.round(dx * GHOST_DRAG_RADIUS / d);
			nz = g.originZ + (int) Math.round(dz * GHOST_DRAG_RADIUS / d);
		}
		g.pos = groundAt(level, new BlockPos(nx, p.y(), nz));
		sendGhostState(player, g);
	}

	private static void handleConfirmGhost(ServerPlayer player) {
		GhostBuilding g = PENDING_GHOSTS.get(player.getUUID());
		if (g == null || g.confirmed) return;
		if (!(player.level() instanceof ServerLevel level)) return;

		if (g.kind == SpawnGhostPayload.KIND_TOWNHALL) {
			// Settlers found the town hall: they vanish, the structure is
			// placed, and a starter builder squad spawns nearby as a "reward".
			for (UUID u : g.units) {
				Entity e = level.getEntity(u);
				if (e != null) e.discard();
				MOVE_TARGETS.remove(u);
			}
			buildTownHall(level, g.pos);
			BlockPos builderSpot = findClearGround(level, g.pos.offset(5, 0, 0));
			com.civcraft.item.SettlerCharterItem.spawnBuilderSquadAt(level, builderSpot);
			PENDING_GHOSTS.remove(player.getUUID());
			sendGhostCleared(player);
			player.displayClientMessage(Component.literal("§6Ратуша заложена. Строители прибыли."), true);
		} else {
			// Builder ghost — instant build on confirm, Civ-style costs
			// (production / gold). Consumes one builder charge.
			int[] res = PLAYER_RESOURCES.computeIfAbsent(player.getUUID(), u -> new int[]{30, 40, 10, 0, 0});
			int needProd = 0, needGold = 0;
			String name;
			switch (g.kind) {
				case SpawnGhostPayload.KIND_SMITHY     -> { needProd = 50; needGold = 10; name = "Кузница"; }
				case SpawnGhostPayload.KIND_SAWMILL    -> { needProd = 30; name = "Лесопилка"; }
				case SpawnGhostPayload.KIND_STOREHOUSE -> { needProd = 20; name = "Склад"; }
				case SpawnGhostPayload.KIND_QUARRY     -> { needProd = 30; name = "Каменоломня"; }
				default -> name = "Здание";
			}
			if (res[1] < needProd || res[2] < needGold) {
				player.displayClientMessage(Component.literal(String.format(
						"§cНе хватает ресурсов (нужно: %d производства, %d золота)",
						needProd, needGold)), true);
				return;
			}
			res[1] -= needProd;
			res[2] -= needGold;
			sendResources(player, res);
			completeBuilding(level, g);
			for (UUID u : g.units) {
				Entity b = level.getEntity(u);
				if (b != null) { b.discard(); MOVE_TARGETS.remove(u); break; }
			}
			PENDING_GHOSTS.remove(player.getUUID());
			sendGhostCleared(player);
			player.displayClientMessage(Component.literal("§6" + name + " построена."), true);
		}
	}

	private static void handleCancelGhost(ServerPlayer player) {
		GhostBuilding g = PENDING_GHOSTS.remove(player.getUUID());
		if (g == null) return;
		// Detach any carriers still on this job.
		for (UUID u : g.units) CARRIERS.remove(u);
		sendGhostCleared(player);
	}

	private static void sendGhostState(ServerPlayer player, GhostBuilding g) {
		ServerPlayNetworking.send(player, new GhostStatePayload(
				g.kind, g.pos.getX(), g.pos.getY(), g.pos.getZ(),
				g.originX, g.originZ,
				g.progress, g.target, g.confirmed));
	}

	private static void sendGhostCleared(ServerPlayer player) {
		ServerPlayNetworking.send(player, GhostStatePayload.cleared());
	}

	private static BlockPos groundAt(ServerLevel level, BlockPos p) {
		int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ());
		return new BlockPos(p.getX(), gy, p.getZ());
	}

	private static void tickCarriers(net.minecraft.server.MinecraftServer server) {
		if (CARRIERS.isEmpty()) return;
		for (var it = CARRIERS.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			UUID carrierId = entry.getKey();
			CarrierJob job = entry.getValue();
			GhostBuilding g = job.target;
			if (g == null || !ACTIVE_BUILDS.contains(g)) { it.remove(); continue; }

			Entity e = null;
			ServerLevel lv = null;
			for (ServerLevel sl : server.getAllLevels()) {
				Entity eh = sl.getEntity(carrierId);
				if (eh != null) { e = eh; lv = sl; break; }
			}
			if (e == null) { it.remove(); continue; }

			switch (job.phase) {
				case TO_HALL -> {
					BlockPos hall = findNearestTownHall(lv, e.blockPosition());
					if (hall == null) return;
					if (stepToward(lv, e, hall, 1.5)) {
						job.phase = CarrierPhase.WAIT_HALL;
						job.waitTicks = CARRIER_WAIT_TICKS;
					}
				}
				case WAIT_HALL -> {
					if (--job.waitTicks <= 0) job.phase = CarrierPhase.TO_GHOST;
				}
				case TO_GHOST -> {
					if (stepToward(lv, e, g.pos, 1.5)) {
						job.phase = CarrierPhase.WAIT_GHOST;
						job.waitTicks = CARRIER_WAIT_TICKS;
					}
				}
				case WAIT_GHOST -> {
					if (--job.waitTicks <= 0) {
						g.progress++;
						if (g.progress >= g.target) {
							completeBuilding(lv, g);
							e.discard();  // one builder consumed on completion
							it.remove();
							ACTIVE_BUILDS.remove(g);
							// Detach any other carriers — this build is done.
							for (var it2 = CARRIERS.entrySet().iterator(); it2.hasNext(); ) {
								if (it2.next().getValue().target == g) it2.remove();
							}
						} else {
							job.phase = CarrierPhase.TO_HALL;
						}
					}
				}
			}
		}
	}

	private static BlockPos findNearestTownHall(ServerLevel lv, BlockPos from) {
		int r = 64;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestD2 = Double.MAX_VALUE;
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dz = -r; dz <= r; dz += 2) {
				int gy = lv.getHeight(Heightmap.Types.WORLD_SURFACE, from.getX() + dx, from.getZ() + dz);
				for (int dy = -4; dy <= 10; dy++) {
					m.set(from.getX() + dx, gy + dy, from.getZ() + dz);
					if (lv.getBlockState(m).getBlock() == ModBlocks.TOWN_HALL) {
						double d2 = dx*dx + dz*dz;
						if (d2 < bestD2) { bestD2 = d2; best = m.immutable(); }
						break;
					}
				}
			}
		}
		return best;
	}

	private static void completeBuilding(ServerLevel level, GhostBuilding g) {
		switch (g.kind) {
			case SpawnGhostPayload.KIND_SMITHY -> buildSmithy(level, g.pos);
			case SpawnGhostPayload.KIND_SAWMILL -> {
				buildSawmill(level, g.pos);
				for (int i = 0; i < 2; i++) {
					var lj = com.civcraft.item.SettlerCharterItem.spawnLumberjackAt(
							level, g.pos.offset(i - 1, 1, 2));
					if (lj != null) LUMBERJACKS.put(lj.getUUID(), new LumberjackJob(g.pos, g.owner));
				}
			}
			case SpawnGhostPayload.KIND_STOREHOUSE -> buildStorehouse(level, g.pos);
			case SpawnGhostPayload.KIND_QUARRY     -> buildQuarry(level, g.pos);
		}
	}

	private static void buildStorehouse(ServerLevel level, BlockPos base) {
		if (placeFromTemplate(level, base, "storehouse")) return;
		var wood = ModBlocks.STOREHOUSE.defaultBlockState();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlockAndUpdate(base.offset(x, 0, z), wood);
				if (!(x == 0 && z == 0)) level.setBlockAndUpdate(base.offset(x, 1, z), wood);
			}
		}
	}

	private static void buildQuarry(ServerLevel level, BlockPos base) {
		if (placeFromTemplate(level, base, "quarry")) return;
		var stone = ModBlocks.QUARRY.defaultBlockState();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlockAndUpdate(base.offset(x, 0, z), stone);
			}
		}
	}

	private static void buildSmithy(ServerLevel level, BlockPos base) {
		if (placeFromTemplate(level, base, "smithy")) return;
		var iron = ModBlocks.SMITHY.defaultBlockState();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlockAndUpdate(base.offset(x, 0, z), iron);
			}
		}
	}

	private static void buildSawmill(ServerLevel level, BlockPos base) {
		if (placeFromTemplate(level, base, "sawmill")) return;
		var planks = ModBlocks.SAWMILL.defaultBlockState();
		var oak = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlockAndUpdate(base.offset(x, 0, z), planks);
			}
		}
		BlockPos[] corners = {
				base.offset(-1, 1, -1), base.offset( 1, 1, -1),
				base.offset(-1, 1,  1), base.offset( 1, 1,  1)
		};
		for (BlockPos c : corners) {
			level.setBlockAndUpdate(c, oak);
			PROTECTED_LOGS.add(c.immutable());
		}
	}

	private static void tickLumberjacks(net.minecraft.server.MinecraftServer server) {
		if (LUMBERJACKS.isEmpty()) return;
		for (var it = LUMBERJACKS.entrySet().iterator(); it.hasNext(); ) {
			var entry = it.next();
			Entity e = null;
			ServerLevel found = null;
			for (ServerLevel lv : server.getAllLevels()) {
				Entity eh = lv.getEntity(entry.getKey());
				if (eh != null) { e = eh; found = lv; break; }
			}
			if (e == null) { it.remove(); continue; }
			tickLumberjack(found, e, entry.getValue(), server);
		}
	}

	private static void tickLumberjack(ServerLevel level, Entity lj, LumberjackJob job,
	                                   net.minecraft.server.MinecraftServer server) {
		switch (job.phase) {
			case TO_LOG -> {
				if (job.target == null) {
					job.target = findNearestLog(level, lj.blockPosition());
					if (job.target == null) return;  // no trees nearby, idle
				}
				if (stepToward(level, lj, job.target, 0.8)) {
					job.phase = WorkPhase.CHOP;
					job.chopTicks = 0;
				}
			}
			case CHOP -> {
				job.chopTicks++;
				if (job.chopTicks >= CHOP_DURATION_TICKS) {
					if (job.target != null && isLog(level, job.target)
							&& !PROTECTED_LOGS.contains(job.target)) {
						level.setBlockAndUpdate(job.target,
								net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
					job.target = job.home;
					job.phase = WorkPhase.TO_HOME;
				}
			}
			case TO_HOME -> {
				if (stepToward(level, lj, job.home, 1.5)) {
					job.phase = WorkPhase.DEPOSIT;
				}
			}
			case DEPOSIT -> {
				int[] r = PLAYER_RESOURCES.get(job.owner);
				if (r != null) {
					r[1] += PRODUCTION_PER_CHOP;  // Civ-style: chopping yields production
					ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
					if (owner != null) sendResources(owner, r);
				}
				job.target = null;
				job.phase = WorkPhase.TO_LOG;
			}
		}
	}

	private static boolean stepToward(ServerLevel level, Entity e, BlockPos target, double arriveRadius) {
		double tx = target.getX() + 0.5;
		double tz = target.getZ() + 0.5;
		double dx = tx - e.getX();
		double dz = tz - e.getZ();
		double flat = Math.hypot(dx, dz);
		if (flat < arriveRadius) return true;
		double nx = e.getX() + dx / flat * LUMBERJACK_STEP;
		double nz = e.getZ() + dz / flat * LUMBERJACK_STEP;
		int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(nx), (int) Math.floor(nz));
		float yaw = (float) Math.toDegrees(Math.atan2(-dx / flat * LUMBERJACK_STEP, dz / flat * LUMBERJACK_STEP));
		e.snapTo(nx, gy, nz, yaw, 0f);
		return false;
	}

	private static boolean isLog(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS);
	}

	private static BlockPos findNearestLog(ServerLevel level, BlockPos origin) {
		BlockPos best = null;
		double bestD2 = Double.MAX_VALUE;
		int r = LUMBERJACK_SEARCH_RADIUS;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				// MOTION_BLOCKING_NO_LEAVES returns top of trunk for tree columns
				// (skipping the leaf canopy). Scan a few blocks down from there
				// to find the bottom-most log of the trunk we want to chop.
				int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						origin.getX() + dx, origin.getZ() + dz);
				for (int dy = 0; dy >= -8; dy--) {
					m.set(origin.getX() + dx, topY + dy, origin.getZ() + dz);
					if (!isLog(level, m)) continue;
					if (PROTECTED_LOGS.contains(m)) break;  // player building, leave whole column
					double d2 = dx * dx + dz * dz;
					if (d2 < bestD2) {
						bestD2 = d2;
						best = m.immutable();
					}
					break;
				}
			}
		}
		return best;
	}

	private static void handleSpawnSettlers(ServerLevel level, SpawnSettlersPayload payload) {
		net.minecraft.core.BlockPos p = payload.pos();
		if (level.getBlockState(p).getBlock() != ModBlocks.TOWN_HALL) {
			LOGGER.info("[CivCraft] SpawnSettlers rejected: no town hall at {}", p);
			return;
		}
		int sx = p.getX() - 2;
		int sz = p.getZ() - SPAWN_SOUTH_OFFSET;
		int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, sx, sz);
		net.minecraft.core.BlockPos anchor = new net.minecraft.core.BlockPos(sx, gy, sz);
		com.civcraft.item.SettlerCharterItem.spawnSquadAt(level, anchor);
		LOGGER.info("[CivCraft] Spawned settlers: spire={} anchor={}", p, anchor);
	}

	public static final int TOWN_HALL_Y_OFFSET = -3;

	private static void buildTownHall(ServerLevel level, net.minecraft.core.BlockPos base) {
		// Sink the town hall 3 blocks into the ground — matches client ghost preview.
		BlockPos anchor = base.offset(0, TOWN_HALL_Y_OFFSET, 0);
		if (placeFromTemplate(level, anchor, "townhall")) {
			// Spire sits on top of the now-sunken structure.
			level.setBlockAndUpdate(anchor.offset(0, 5, 0), ModBlocks.TOWN_HALL.defaultBlockState());
			return;
		}
		var cobble = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
		var planks = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
		var glass  = net.minecraft.world.level.block.Blocks.GLASS_PANE.defaultBlockState();
		var log    = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
		var air    = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				level.setBlockAndUpdate(anchor.offset(x, 0, z), cobble);
				level.setBlockAndUpdate(anchor.offset(x, 4, z), planks);
			}
		}
		for (int y = 1; y <= 3; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
					if (!edge) { level.setBlockAndUpdate(anchor.offset(x, y, z), air); continue; }
					boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
					boolean isDoorColumn = (x == 0 && z == -2);
					boolean isWindow = y == 2 && !corner && !isDoorColumn;
					var use = corner ? log : (isWindow ? glass : cobble);
					BlockPos at = anchor.offset(x, y, z);
					level.setBlockAndUpdate(at, use);
					if (corner) PROTECTED_LOGS.add(at.immutable());
				}
			}
		}
		level.setBlockAndUpdate(anchor.offset(0, 1, -2), air);
		level.setBlockAndUpdate(anchor.offset(0, 2, -2), air);
		level.setBlockAndUpdate(anchor.offset(0, 5, 0), ModBlocks.TOWN_HALL.defaultBlockState());
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
			mob.setNoAi(true);
			mob.getNavigation().stop();
			i++;
		}
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
			if (flat < ARRIVE_RADIUS) { it.remove(); continue; }

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
