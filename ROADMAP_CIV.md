# CivCraft → Civilization Clone — Roadmap

Этот документ описывает **пошаговый план** превращения текущего RTS-мода
(real-time с drag-placement и непрерывным временем) в клон
Sid Meier's Civilization (пошаговый, с эпохами, деревом технологий,
лимитом движения, клетками-шестигранниками/квадратами).

Существующий каркас уже даёт: ресурсы (food, production, gold, science,
culture), здания-призраки, отряды-юниты, камера top-down, выделение.
Ниже — что надо ДОБАВИТЬ и ПЕРЕРАБОТАТЬ.

---

## Фаза 1 — Пошаговая система (базис всего остального)

Без этого никакой другой Civ-механики не будет. Переделка самая большая.

### 1.1 Состояние хода (server-side)

Новый класс `TurnState`:
```
year         : int       // 4000 BC … 2100 AD, шаг зависит от эры
turnNumber   : int       // 1, 2, 3 …
activePlayer : UUID      // в мультиплеере — очередь
phase        : PHASE enum {START, PLAYER_ACTIONS, COMBAT_RESOLUTION, END}
```

Храним в `ServerLevel` как attached data, сериализуем в save.

### 1.2 End-turn flow

1. Клиент шлёт `EndTurnPayload` (C2S).
2. Сервер валидирует: все обязательные действия совершены (см. фазу 2).
3. Сервер выполняет end-of-turn:
   - Копит ресурсы (food от ферм, production от шахт, science/culture)
   - Тик производства зданий/юнитов (прогрессбары)
   - Рост городов
   - Завершение исследований
4. `TurnState.turnNumber++`, `year += yearsPerTurn(era)`.
5. S2C `TurnAdvancedPayload` → клиент обновляет HUD/год.

### 1.3 UI кнопка "Конец хода"

В правом нижнем углу `CivcraftHud`: жирная синяя кнопка «End Turn».
Мерцает красным, если остались юниты с непотраченными movement-points.
Горячая клавиша: **Enter** или **Space**.

### 1.4 Что убрать/заморозить

- Real-time движение юнитов по `MOVE_TARGETS` / `tickMovement` —
  **отключить**. Теперь юниты двигаются моментально (teleport) при
  исполнении приказа, а не каждый tick.
- `LumberjackJob` и `CarrierJob` AI — **отключить**. Работа переводится
  на "+N production per turn" от зданий (см. фазу 4).
- Ghost drag-и-drop остаётся, но confirm выполняется только в свой ход.

### 1.5 Файлы, которые надо создать / изменить

| Файл | Что делать |
|---|---|
| `com.civcraft.turn.TurnState` | новый |
| `com.civcraft.turn.TurnManager` | новый — серверный контроллер |
| `com.civcraft.network.EndTurnPayload` | новый C2S |
| `com.civcraft.network.TurnStatePayload` | новый S2C |
| `CivcraftHud.drawEndTurnButton` | новый метод |
| `Civcraft.java` | убрать `tickMovement`, `tickLumberjacks`, `tickCarriers` из `ServerTickEvents`; заменить на end-turn ticks |

---

## Фаза 2 — Ограничение дальности движения

Каждый юнит имеет `movementPoints` и тратит их при приказе на передвижение.

### 2.1 Данные на юните

В `CompoundTag` юнита через `CustomData` (или attached через mixin):
```
maxMovement : int  // 2 для поселенцев, 4 для кавалерии, 1 для осадных
remaining   : int  // обнуляется на start-of-turn до maxMovement
```

### 2.2 Стоимость клетки

Каждый блок/клетка = 1 movement point по плоской поверхности, +1 за:
- лес (penalty × 2)
- холмы (+1)
- болота (+2)
- реки без моста (+1 пересечение)
- диагональ (×1.414 округляется до целого)

Реализация: `MovementCost.get(ServerLevel, BlockPos, BlockPos)`.

### 2.3 Подсветка дальности

Когда юнит выделен:
- Client-side Dijkstra от позиции юнита на глубину `remaining`.
- Результат: `Set<BlockPos>` достижимых клеток.
- Рендер: зелёный полупрозрачный блок-overlay на каждой (как drag-ring).
- Недостижимые, но видимые на ходу: жёлтые.
- За пределами — без подсветки.

Это делается одним кадром Dijkstra на выделение; нет per-tick нагрузки.

### 2.4 Приказ движения

`MoveOrderPayload` (уже есть) меняет семантику:
- Сервер проверяет, что путь ≤ `remaining`.
- Если да — мгновенно `entity.snapTo(target)`; `remaining -= cost`.
- Если нет — "Move as far as possible": находит ближайшую точку на пути,
  переставляет туда, обнуляет `remaining`.

Визуально приказ исполняется через 0.3s easing-анимацию на клиенте
(lerp между старой и новой позицией), чтобы не было телепорт-рывка.

### 2.5 Файлы

| Файл | Что делать |
|---|---|
| `com.civcraft.unit.MovementPoints` | новый — attached data |
| `com.civcraft.unit.MovementCost` | новый — статические хелперы |
| `com.civcraft.client.movement.ReachableTilesOverlay` | новый — рендер |
| `Civcraft.handleMoveOrder` | переделать с RT на turn-based |

---

## Фаза 3 — Система эпох

Эпоха даёт: новый визуал, новые юниты/здания/технологии, лимиты того,
что можно строить.

### 3.1 Enum эпох

```
enum Era {
  ANCIENT,       // 4000 BC (старт)
  CLASSICAL,     // 1000 BC
  MEDIEVAL,      // 500 AD
  RENAISSANCE,   // 1500 AD
  INDUSTRIAL,    // 1800 AD
  MODERN,        // 1900 AD
  INFORMATION    // 1990 AD
}
```

Каждая несёт:
- `yearsPerTurn` — 40 в Ancient, 5 в Modern
- `minTurn`     — кол-во ходов до неё (для alternative: trigger by tech)

### 3.2 Переход по эпохам

Два параллельных триггера:
1. **Calendar**: `year >= era.startYear` → автоматический переход.
2. **Tech-based**: исследование ключевого tech (напр. `IRON_WORKING`
   открывает `CLASSICAL`). Можно обогнать календарь.

На переходе:
- `TurnState.era = newEra`
- S2C `EraChangedPayload` → клиенты играют cinematic (см. 3.5)
- Сервер разблокирует юниты/здания этой эпохи в dispatcher

### 3.3 Epoch-gate для зданий/юнитов

Каждый `BuildingKind` и `UnitKind` имеет поле `minEra`:
```
TownHall      → ANCIENT
Barracks      → ANCIENT
Library       → CLASSICAL
Smithy        → MEDIEVAL
Stables       → MEDIEVAL
Bank          → RENAISSANCE
Factory       → INDUSTRIAL
```

При попытке спавна призрака более позднего здания сервер отказывает.

### 3.4 Визуальные отличия эпох

- **Ancient**: соломенные крыши, землянки, круглые дома
- **Classical**: каменная кладка, колонны, мраморные акценты
- **Medieval**: текущий town hall, башни с зубцами
- **Renaissance**: кирпичные здания, черепица, барокко
- **Industrial**: кирпич с трубами, паровые машины
- **Modern**: бетон/стекло, небоскрёбы

Техническая реализация: каждый `BuildingKind` имеет вариант шаблона
для каждой эпохи в `data/civcraft/structures/<kind>_<era>.nbt`. При
апгрейде эпохи сервер может автоматически заменить старые здания
новыми моделями (опционально, сперва — только новые постройки).

### 3.5 Cinematic перехода

На клиенте:
- Полноэкранное затемнение 2 сек
- Золотой титл "Эпоха: Классическая"
- Звук колокола/рога
- HUD краткая справка: что разблокировалось

Реализация: новый `EraTransitionScreen extends Screen`.

### 3.6 Файлы

| Файл | Что делать |
|---|---|
| `com.civcraft.era.Era` | новый enum |
| `com.civcraft.era.EraGate` | новый — проверка разблокировки |
| `com.civcraft.network.EraChangedPayload` | новый S2C |
| `com.civcraft.client.gui.EraTransitionScreen` | новый |
| `SpawnGhostPayload` handler | проверять `EraGate.canBuild(kind, era)` |

---

## Фаза 4 — Дерево технологий (Tech tree)

Раз есть эпохи, нужна причина движения по ним.

### 4.1 Структура

JSON в `data/civcraft/tech/*.json`:
```json
{
  "id": "pottery",
  "era": "ANCIENT",
  "cost_science": 25,
  "prerequisites": [],
  "unlocks": {
    "buildings": ["granary"],
    "units": []
  }
}
```

Tree loader читает на старте мира и держит `Map<String, Tech>` на сервере.

### 4.2 UI дерева

Новый `TechTreeScreen extends Screen`:
- Pan+zoom по большой карте деревьев (как Civ 5/6)
- Каждый tech = узел; цвет = эпоха
- Стрелки = prerequisites
- Клик → select; кнопка "Исследовать"

Горячая клавиша: **T**.

### 4.3 Процесс исследования

- `currentResearch : String|null` в PlayerState
- В конце хода: `science -= tech.cost`; если cost списан, tech completed
  - Unlock buildings/units в dispatcher
- Пока исследование не завершено, вся научная прибыль идёт на него

---

## Фаза 5 — Города вместо ратуши

### 5.1 City как сущность

Currently: «ратуша» — просто блок. В Civ город — комплексная сущность:
- Имя
- Население (pop, растёт от food surplus)
- Границы (радиус клеток, расширяется с культурой)
- Очередь производства (building/unit list)
- Yield tiles (клетки вокруг, дающие food/prod/gold/science/culture)

Реализация: новый `City` class с сохранением в world data.

### 5.2 Захват клеток

Каждая клетка в радиусе города имеет owner. Overlap решается по ближайшему городу.
Визуально: окантовка цветом фракции игрока в мире.

### 5.3 Производство в городах

Вместо строителей-отрядов: каждый город имеет очередь.
- В очередь добавляются здания/юниты из доступных (по эпохе + tech)
- Каждый ход тратится `cityProduction` очков на первый элемент
- По завершении: spawn юнита на клетке города / построить здание

Это **заменяет** текущую систему drag-ghost от строителей — для зданий
**городских** (Library, Bank, Granary). Drag-placement остаётся для
*территориальных* построек вне города (Farm, Mine, Lumbermill, Fort).

---

## Фаза 6 — Боевая система

### 6.1 Юниты с характеристиками

В attached data:
```
unitKind    : UnitKind enum  // WARRIOR, ARCHER, SWORDSMAN, KNIGHT …
attack      : int
defense     : int
hp          : int (max 100)
rangedAttack: int (0 если рукопашный)
range       : int (клетки, 0 если рукопашный)
```

### 6.2 Fight resolution

При приказе атаки:
- Считается `attacker.attack` vs `defender.defense`
- Damage обоим: по формуле `30 * exp((atk-def)*0.04)`
- Оба юнита теряют HP, победитель остаётся

Для ranged: defender не отвечает.

Ход юнита тратится на атаку: `remaining = 0` после боя.

### 6.3 Anti-cheese:
- Юнит может атаковать только если `remaining >= 1`
- Осада (siege) требует 2+ хода adjacentности к городу

---

## Фаза 7 — Дипломатия и фракции

- Фракции на старте (в `GameOptions`): игроки выбирают одну из 8-10
  (Россия/Рим/Египет/Вавилон/Греция/Германия/Англия/Япония/Монголия)
- Каждая имеет уникальный бонус (+10% science, +1 movement, etc.)
- Отношения между фракциями: peace / war / alliance / trade
- Новый `DiplomacyScreen` — матрица отношений + кнопки «предложить мир»

---

## Фаза 8 — Условия победы

Классические Civ-варианты:
1. **Domination** — захватить все столицы
2. **Science** — построить spaceship (индустриальная эпоха)
3. **Culture** — 5 уровней культуры во всех городах
4. **Diplomatic** — мировой конгресс
5. **Score** — самый большой score к 2050 AD

UI: экран «Победа!» с выбранным игроком.

---

## Полный список того, что надо реализовать (по порядку)

### Критический путь (Civ-core)

- [ ] Turn state + end-turn button + yearsPerTurn
- [ ] Убрать real-time tick movement/lumberjacks
- [ ] MovementPoints per unit, reset on start-of-turn
- [ ] Reachable-tiles overlay (Dijkstra)
- [ ] Era enum + EraGate + auto-transition
- [ ] Era-cinematic screen
- [ ] Tech tree data + TechTreeScreen
- [ ] Science accumulation flows into currentResearch

### Важное (playable loop)

- [ ] City entity + population growth
- [ ] City production queue
- [ ] Tile ownership + borders rendering
- [ ] Combat: attack/defense/HP + fight resolution
- [ ] Фракции + uniques
- [ ] Diplomacy screen (минимально: peace/war)

### Полировка

- [ ] Era-specific building variants (.nbt per era)
- [ ] Tech-tree animated unlocks
- [ ] Wonders (уникальные глобальные постройки)
- [ ] Great people (гении, артисты)
- [ ] Religions (optional, Civ 4/6 feature)
- [ ] Sound: эпохальные треки, битва, notifications
- [ ] Save/load robust (world attached data serialization)

### Техдолг с текущей базой

- [ ] Удалить `ResourceState` client-side stub, сделать через S2C sync только
- [ ] Merge `PENDING_GHOSTS` + `ACTIVE_BUILDS` в единый `ConstructionManager`
- [ ] Вынести все константы стоимостей в JSON datapack
- [ ] Переписать `CivcraftHud` на слои (top/bottom/modal) для расширяемости

---

## Оценка сложности и времени

| Фаза | Сложность | Время* |
|---|---|---|
| 1. Пошаговость | ★★★★☆ | 2-3 дня |
| 2. Movement range | ★★★☆☆ | 1-2 дня |
| 3. Эпохи | ★★★☆☆ | 1 день |
| 4. Tech tree | ★★★★☆ | 2-3 дня |
| 5. Города | ★★★★★ | 3-5 дней |
| 6. Бой | ★★★★☆ | 2-3 дня |
| 7. Дипломатия | ★★★☆☆ | 1-2 дня |
| 8. Победа | ★★☆☆☆ | 1 день |

\* Это "работа-дни" одного разработчика на full-time, с учётом тестов и
итераций. В реальности можно умножить на 1.5-2.

Полный клон Civ — **3-4 недели** интенсивной работы минимум.
MVP (фазы 1-3) — **4-7 дней**.

---

## Рекомендуемый порядок действий

1. Сначала **фаза 1**: без пошаговости ничего не работает.
2. Затем **фаза 2** параллельно с первыми тестами пошаговости.
3. **Фаза 3** сразу после — это самый видимый игроку прогресс.
4. На этом этапе мод уже играется как Civ-like.
5. Потом итеративно углублять: tech tree → cities → combat → diplomacy.

На каждом этапе — отдельный коммит, отдельный тест, отдельный PR.
