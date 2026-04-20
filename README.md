# CivCraft

RTS-style Fabric mod for Minecraft 1.21.11: isometric camera, squad-based settler
units, click-and-drag selection, right-click move orders with real pathfinding,
a turn-based day-cycle, and an in-world HUD with perk buttons.

## Features

- **Isometric camera** (press `C`) — spectator-like free pan with WASD, `Z`/`X`
  rotation, mouse-wheel zoom. Cursor is permanently unlocked in iso mode.
- **Settler squads** via the "Settler's Charter" item: 3 villagers + 1 mule +
  an invisible leader, all glued together by a proximity-based squad link.
- **RTS controls**: LMB drag-select, RMB to march. Orders run through a custom
  networking packet; movement is a direct per-tick step, so the squad marches
  in a tight grid formation on the terrain surface.
- **Town Hall perk**: select a squad and click the perk button (or `Q`) to
  consume the squad and build a small cobblestone town hall (5×5 footprint with
  walls, windows, planks roof, door, and a Town Hall block spire on top).
- **Building perks**: click the Town Hall spire to select the building and use
  the perk button to spawn a new settler squad next to it.
- **Next-turn mechanic**: press the planet icon bottom-right (or `Space`) to
  fast-forward the day cycle through night and back to morning. `doDaylightCycle`
  is disabled so time only advances when you ask.
- **In-world overlays**: rings under squad members (white when selected),
  aggregated march-to-target trajectory, billboarded "⚔ Поселенцы" banners.
- **Pixel-art HUD**: animated Earth button for next turn, perk icons tinted
  from Figma's `Game Icons` community library.

## Keybinds

| Key | Action |
|---|---|
| `C` | Toggle isometric camera (enters spectator mode) |
| `WASD` | Pan camera along its yaw axis |
| `Z` / `X` | Rotate camera ±5° |
| Mouse wheel | Zoom camera (distance 8–80 blocks) |
| LMB drag | Rectangle select — picks whole squads by proximity |
| LMB click on mob | Select that mob's full squad |
| LMB click on Town Hall spire | Select building |
| RMB | Move selected squad to cursor position |
| `Q` | Trigger the active perk (context depends on selection) |
| `Space` | Next turn (day → night → morning animation) |

## Build

The project uses Fabric Loom 1.13.3 against Minecraft 1.21.11. A local
offline maven mirror is required for Loom dependencies because some hosts
(notably `maven.fabricmc.net`) are throttled by DPI on the author's network.
Helper scripts in `tools/` reproduce the mirror:

1. Download the listed jars (see `tools/gen_download_list.py` output) into
   `~/Downloads/civcraft-deps/`.
2. Run `tools/build_local_repo.py` — it copies downloads into `local-repo/`
   with the expected Maven layout and synthesises POMs where needed.
3. `tools/unpack_fabric_api.py` extracts the bundled Fabric API into
   individual submodule artifacts under `local-repo/net/fabricmc/fabric-api/`.
4. `tools/chunked_download.py` can fetch any single HTTPS URL through small
   HTTP Range chunks when DPI truncates full-file downloads.
5. Then `gradle --no-daemon runClient` launches the dev client.

`tools/svg_to_icons.py` rasterises the Figma-exported SVG game icons and
retints them in the CivCraft gold palette.

## Layout

```
src/main/java/com/civcraft/
  Civcraft.java                — server entrypoint, networking, turn clock
  block/TownHallBlock.java     — the Town Hall spire block
  entity/SettlerEntity.java    — invisible squad leader entity
  item/SettlerCharterItem.java — "found new settlement" item, spawns a squad
  network/                     — MoveOrder / FoundTownHall / SpawnSettlers / NextTurn payloads
  registry/                    — block / item / entity / creative tab registries

src/client/java/com/civcraft/client/
  CivcraftClient.java          — client tick: input, selection, RTS orders
  camera/CameraMath.java       — screen ↔ world raycasting for RTS picking
  camera/TopDownMode.java      — camera pose state
  hud/CivcraftHud.java         — planet-shaped HUD + perk bar
  mixin/                       — camera override, crosshair hide, scroll zoom, mouse grab block
  render/OverlayRenderer.java  — in-world rings, trajectory line, banner
  render/SettlerRenderer.java  — empty renderer for the leader entity
  selection/SelectionState.java — current selection (squad units or building)

tools/                          — Python/PowerShell helpers (icons, local repo, log tail)
```

## License

CC0 — template and additions alike.
