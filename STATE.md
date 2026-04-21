# CivCraft — Актуальное состояние

> Документ описывает **экономику** и **реализованный функционал** мода
> на текущий коммит. Обновляется вручную.

---

## 1. Экономика (Civ6-style)

### 1.1 Ресурсы

| Ресурс       | Тип       | Иконка (vanilla) | Описание                       |
|--------------|-----------|------------------|--------------------------------|
| 🌾 Food       | Хранимая | `Items.BREAD`          | Основание ратуши (30 еды).     |
| ⚒ Production  | **Yield** | `Items.IRON_PICKAXE`   | Очки в ход для постройки.      |
| 💰 Gold       | Хранимая | `Items.GOLD_INGOT`     | Доп. стоимость зданий.         |
| 🔬 Science    | **Yield** | `Items.EXPERIENCE_BOTTLE` | Очки в ход (под будущий tech). |
| 🎭 Culture    | **Yield** | `Items.WRITTEN_BOOK`   | Очки в ход (под будущие policies). |

- **Хранимые** ресурсы — накапливаются, списываются при действиях.
- **Yield** — ПОТОК в ход. Не копится, показывается с `+` в HUD.
- Старт: `30 food / 10 gold`, yield'ы нулевые (пока нет зданий).

### 1.2 Yield зданий

| Здание     | Production | Science | Culture |
|------------|-----------:|--------:|--------:|
| Town Hall  | +2         | +1      | +1      |
| Quarry     | +3         |  0      |  0      |
| Sawmill    | +2         |  0      |  0      |
| Smithy     | +1         | +1      |  0      |
| Storehouse | +1         |  0      |  0      |

### 1.3 Стоимость построек

| Здание     | Production | Food | Gold | Примечание                        |
|------------|-----------:|-----:|-----:|-----------------------------------|
| Town Hall  | —          | 30   | —    | Основывают поселенцы, мгновенно.  |
| Storehouse | 6          | —    | —    |                                   |
| Sawmill    | 10         | —    | —    | + 2 лесоруба при постройке.       |
| Quarry     | 10         | —    | —    |                                   |
| Smithy     | 15         | —    | 10   |                                   |

### 1.4 Ход (turn)

- **1 ход = 200 server ticks ≈ 10 секунд реального времени** (пока real-time).
- В конце каждого хода каждый `ACTIVE_BUILD` получает `+yield` очков прогресса.
- Когда `progress ≥ cost` → здание материализуется.

### 1.5 Сценарий стройки

1. Игрок выделяет отряд строителей, жмёт Q/E или иконку в командной панели.
2. Появляется полупрозрачный призрак здания.
3. Перетаскивание (в радиусе 5 блоков) → ✓ подтвердить / ✗ отменить.
4. При подтверждении:
   - Списывается gold cost (если есть).
   - Один строитель удаляется (заряд).
   - Если `yield ≥ prodCost` → **мгновенная** постройка.
   - Иначе → **градуальная**: призрак зелёный, в `ACTIVE_BUILDS`, копит производство каждый ход.

---

## 2. Юниты

| Юнит        | Префикс | Источник              | Логика                          |
|-------------|---------|-----------------------|---------------------------------|
| Поселенец (4) | ⚔      | Старт мира (1-й заход) | Основывают ратушу (1 заряд).   |
| Строитель (3) | ⛏      | После постройки ратуши | Строят здания (3 заряда).      |
| Лесоруб (1+) | 🪓      | Лесопилка (×2)        | AI: рубит бревна → yield.       |

- У каждого отряда **уникальный squad ID**, зашитый в custom name как `§8#N`.
- Выделение рамкой → все юниты с одинаковым sid попадают в выделение.
- Не склеиваются при стоянии рядом (в отличие от proximity-based).

---

## 3. Реализованный функционал

### 3.1 Камера и ввод (Этап 1 roadmap'а частично)

- [x] Top-down изометрическая камера (pitch 60°, yaw 45°, zoom 8–80)
- [x] WASD — плавный pan (float-интерполяция через `partialTicks`)
- [x] MMB drag — pan, Shift+MMB — вращение yaw
- [x] Scroll — зум
- [x] Клавиша **C** — вкл/выкл RTS-режим (+ spectator mode)
- [x] Автовключение RTS через 2s после входа в мир
- [x] Игрок телепортируется к якорю камеры (для стриминга чанков)
- [x] Кастомный курсор (float над HUD)
- [x] `snapshot() / lerp / lerpYaw` — плавность на 60+ FPS с 20Hz инпутом

### 3.2 HUD

- [x] Фиксированный visual scale = 2 (не растягивается с guiScale игрока)
- [x] **Верх**: 5-слотовая ресурс-панель (vanilla item-иконки)
- [x] **Планета** в top-right (пока no-op — wiki отключена из-за blur-бага)
- [x] **Низ, 104px**:
  - Минимап 160×80 (NativeImage + DynamicTexture, sample 400ms/3-block)
  - Панель выбора (заголовок, число юнитов, подсказки по клавишам)
  - Сетка команд 3×3 (зависит от выделения)
- [x] ✓/✗ кнопки для подтверждения/отмены призрака (bottom-center)
- [x] Полупрозрачная рамка выделения при LMB drag

### 3.3 Выделение и приказы

- [x] LMB drag — rectangle select (ID-based grouping)
- [x] RMB — приказ движения (raycast к блоку, не плоская плоскость)
- [x] Q/E/клик по иконке — perk (действие отряда)
- [x] Кольцо вокруг отряда: плоский 1/3-блочный annulus (белое если выделено)
- [x] Траектория: параболическая лента из белых квадов + треугольная стрелка
  - Старт фиксируется при приказе (не «ползёт» пока юнит идёт)
- [x] Real-time движение юнитов (direct stepping, без vanilla AI)
- [x] Форма строя (grid) при массовом приказе

### 3.4 Система призраков

- [x] Sinkронизация `GhostStatePayload` (S2C)
- [x] Spawn/Update/Confirm/Cancel payloads (C2S)
- [x] Полупрозрачный рендер реальных block-состояний через `BlockRenderDispatcher` в `translucentMovingBlock`
- [x] Tank hall: shape грузится из bundled `townhall.nbt`
- [x] 5-блочный drag-радиус + серое кольцо-preview на уровне земли
- [x] 4 стрелки N/S/E/W (клик — shift на 1 блок)
- [x] ✓ в HUD (ПКМ тоже) / ✗ в HUD (Q тоже)
- [x] Town hall утоплена на 3 блока (матч с реальным placement)
- [x] Center-aligned placement (NW-corner → center)
- [x] `BlockIgnoreProcessor.STRUCTURE_AND_AIR` — AIR в шаблонах не затирает terrain

### 3.5 Здания

- [x] **Town Hall** — 5×6×5, с дверью, окнами, log-углами, шпилем. `townhall.nbt` bundled.
- [x] **Smithy** — 3×1×3 iron_block
- [x] **Sawmill** — 3×2×3 планки + log-углы, спавнит 2 лесоруба
- [x] **Storehouse** — 3×2×3 stripped_oak (NEW)
- [x] **Quarry** — 3×1×3 smooth_stone (NEW)
- [x] Блок `TOWN_HALL` как маркер шпиля (для детекции клика)
- [x] `PROTECTED_LOGS` — логи в постройках не трогаются лесорубами

### 3.6 Template-система

- [x] Приоритет загрузки: `config/civcraft/blueprints/*.nbt` → bundled `data/civcraft/structures/*.nbt` → per-world `generated/`
- [x] Классpath-загрузка минуя кривой `StructureTemplateManager.get()`
- [x] Автосоздание `config/civcraft/blueprints/` + README при старте
- [x] `/civcraft help` — показывает путь глобальной папки

### 3.7 Экономика (только что реализовано)

- [x] 5 Civ-style ресурсов, food/gold хранимые, production/science/culture — yield
- [x] `PLAYER_BUILDINGS` — учёт построек per player
- [x] `totalProductionYield(uuid)` — сумма по всем зданиям
- [x] Tick-turns каждые 200 ticks → прогресс всех `ACTIVE_BUILDS`
- [x] Мгновенная стройка если `yield ≥ cost`, иначе градуальная
- [x] HUD показывает yield с `+`, stored без префикса

### 3.8 Стартовый flow

- [x] При первом входе в мир: спавн 1 отряда поселенцев (4 юнита) на западе
- [x] Строители НЕ спавнятся при старте
- [x] После подтверждения основания ратуши — спавн отряда строителей (3 юнита) рядом
- [x] Тег `civcraft_starter_spawned` на игроке → защита от дубликатов при перезаходе
- [x] Ресурсы пересинхронизируются при join'е

### 3.9 Рендер-оверлеи

- [x] `OverlayRenderer` (WorldRenderEvents.AFTER_ENTITIES)
- [x] Корректный порядок: LINES → filled quads → translucent block → banners
- [x] Billboard-текст «⚔ Поселенцы» над каждым отрядом
- [x] Glow-outline на выделенных юнитах (`setGlowingTag`)

### 3.10 Стабильность

- [x] `ClientPlayConnectionEvents.DISCONNECT` чистит `GhostState`/`SelectionState`/`TopDownMode`
- [x] Гард `client.level == null` в начале `clientTick` — нет packet-краша при выходе
- [x] `BlockIgnoreProcessor` предотвращает уничтожение terrain воздухом template'а
- [x] Множественные `ACTIVE_BUILDS` в работе параллельно; PENDING очищается сразу после confirm

---

## 4. НЕ реализовано (roadmap)

- [ ] **Полноценный turn-based режим** (Phase 1): кнопка "End Turn", пауза real-time, turnNumber/year
- [ ] **Лимит движения** юнитов (Phase 2): movementPoints, Dijkstra-overlay достижимости
- [ ] **Эпохи** (Phase 3): Ancient → Classical → … → Information
- [ ] **Tech tree** (Phase 4): `data/civcraft/tech/*.json` + `TechTreeScreen`
- [ ] **Города** (Phase 5): City-сущность, границы, очередь производства, рост населения
- [ ] **Боевая система** (Phase 6): attack/defense, HP, бои
- [ ] **Дипломатия** (Phase 7): фракции, peace/war/alliance, экран переговоров
- [ ] **Условия победы** (Phase 8): domination/science/culture/score
- [ ] **Юниты-воины**: мечник, лучник, арбалетчик, кавалерия
- [ ] **Исследования** в текущих зданиях (Smithy, Academy)
- [ ] **Рабочая Wiki** (`CivcraftWikiScreen`) — сейчас отключена из-за blur-крашa

---

## 5. Файлы-референсы

| Где смотреть                          | Что там                                 |
|---------------------------------------|-----------------------------------------|
| `src/main/java/com/civcraft/Civcraft.java` | Сервер: все handlers, ticks, yield, builds |
| `src/client/.../CivcraftClient.java`  | Клиент: ввод, клик, selection, ghost     |
| `src/client/.../hud/CivcraftHud.java` | HUD: ресурс-бар, bottom-bar, mini-map  |
| `src/client/.../render/OverlayRenderer.java` | Рендер колец, траекторий, billboard      |
| `src/client/.../building/GhostState.java` | Shape/bounds/origin призраков           |
| `ROADMAP_CIV.md`                      | План превращения в Civ-клон             |
