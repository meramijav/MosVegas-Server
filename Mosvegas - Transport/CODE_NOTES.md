# Заметки к коду Slim Vehicle

В исходниках нет комментариев. Всё, что объясняет код, собрано здесь. Классы, шейдеры, `build.gradle` и скрипты в `tools/` остаются чистыми.

## Правила (обязательны для людей и ИИ-агентов)

1. **Читать.** Перед изменением файла найди здесь его раздел (по пути, имени класса или метода) и прочитай заметки. В них причины решений, единицы измерения, договорённости между классами и шейдерами, а также то, что уже пробовали и почему от этого отказались.
2. **Не комментировать код.** Ни `//`, ни `/* */`, ни javadoc, ни `#` в скриптах. Любое пояснение пишется сюда.
3. **Записывать.** Изменение кода, которому нужно пояснение, приходит вместе с заметкой здесь. Изменил поведение, константу или смысл метода — поправь его заметку. Удалил или переименовал код — удали или переименуй заметку. Устаревшая заметка хуже отсутствующей.

## Формат

- `## путь/к/файлу` — раздел файла. Заметки сразу под заголовком файла относятся к самому файлу или его главному типу.
- `### Тип` — поля, методы и вложенные типы. Вложенный тип пишется через точку: `### AJSONInteractable.Damage`.
- `### Тип.метод(типы параметров)` — заметки к строкам внутри метода.
- Каждая заметка — пункт списка. В обратных кавычках — объявление или строка кода (или её начало, обрезанное многоточием `…`), к которой она относится, после тире — пояснение.
- Номера строк не пишутся: они сдвигаются при любой правке. Место в коде находят поиском по тексту из обратных кавычек.
- Текст заметок — на английском, как у существующих.

## Architecture

- The mod is split like Immersive Vehicles: `slimcore` is the core (Immersive's `mccore`) and `sliminterfacefabric` binds it to Fabric 1.20.4 (Immersive's `mcinterfaceforge1201`). Both are source roots of one Gradle project (`build.gradle` `sourceSets`), so one jar ships both; the split is kept by `checkCoreBoundary`.
- The core knows nothing of Minecraft, Fabric, Mixin or a renderer library: it compiles against the JDK, Gson, slf4j and JOML alone (`checkCoreBoundary`). Vectors, boxes and the maths are its own (`Point3D`, `BoundingBox`, `SlimMath`), exact copies of vanilla's `Vec3`, `AABB` and `Mth`, so every physics number came out the same after the split. Everything it needs from the game goes through the interfaces in `sliminterface` (world, entities, players, NBT, items, inventories, damage, synced data), implemented by `sliminterfacefabric/wrapper`. Porting to another loader or game version rewrites the interface layer only.
- Core entities are plain objects. The game entity is `BuilderVehicle` (Immersive's `BuilderEntityExisting`): it owns position, rotation, passengers, tracking and saving like any vanilla entity, holds an `EntityVehicleF_Physics` and forwards its ticks, data, NBT, clicks, damage and riders to it. The core reaches its host through `IWrapperEntity host`.
- Entities form one inheritance chain, each level adding one responsibility (see README). Definitions mirror it: `AJSONItem` → `AJSONInteractable` → `AJSONMultipart` → `JSONVehicle`, and each entity level is generic over the definition level it needs.
- Models and animations are read and posed in the core (`rendering`: Bedrock geometry, animation files, easing, a small MoLang); the client only turns the posed bones into vertices (`VehicleRenderer`). No GeckoLib.

## slimcore/src/main/java/studio/merami/slimcore/SlimCore.java

- `class SlimCore` — Mod id, the core's logger and `id(path)` for resource ids of the mod's own namespace. Pack content may use other namespaces.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/Point3D.java

- `class Point3D` — The core's vector, a copy of vanilla's `Vec3` (same fields, same operations, same rounding), so physics written for `Vec3` gives identical results. The interface converts at the boundary (`Convert#vec`, `Convert#point`).
- `return length < 1e-4 ? ZERO : new Point3D(x / length, y / length, z / length);` — Like `Vec3#normalize`: a vector shorter than 1e-4 normalizes to zero, not to a huge unit vector. Code that tests `normalize()` for zero relies on it.
- `public Point3D yRot(float angle)` — Rotations use `SlimMath`'s table sine like vanilla's, not `Math.sin`; the hull, the riders and every chassis transform were tuned against those values.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/BoundingBox.java

- `class BoundingBox` — The core's axis-aligned box, a copy of vanilla's `AABB`. Corners are sorted on construction.
- `return minX < x1 && maxX > x0 && minY < y1 && maxY > y0 && minZ < z1 && maxZ > z0;` — Strict like `AABB#intersects`: boxes that only touch do not intersect, so a car resting on a block top is not inside it.
- `public boolean contains(Point3D point)` — Half-open like `AABB#contains`: the min faces are inside, the max faces outside.
- `public Optional<Point3D> clip(Point3D start, Point3D end)` — `AABB#clip`'s slab test with its 1e-7 epsilon, so rays (projectiles, the glass ray, clicks) hit the same faces as before.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/SlimMath.java

- `class SlimMath` — The parts of vanilla's `Mth` the core uses, bit for bit (checked against the game jar's bytecode): the 65536-entry sine table and its index scale, `floor` that rounds towards negative infinity, `clamp`, `lerp` with the t-first argument order, `approach`, `wrapDegrees` into [-180, 180) and `rotLerp`.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/Text.java

- `class Text` — A message or label for a player: a translation key, an optional fallback shown when the key has no translation (`withFallback`), and arguments. The interface turns it into a `Component` (`Convert#text`); arguments that are `Text` themselves are converted too.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/ResourceId.java

- `record ResourceId` — `namespace:path` with vanilla's `ResourceLocation` character rules and its `minecraft` default namespace, so an id that is valid here is valid in the game and pack errors are reported at parse time, not later by the game.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/BlockPosition.java

- `record BlockPosition` — A block's integer position; `containing` floors like `BlockPos#containing`.

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/IInterfaceCore.java

- `interface IInterfaceCore` — Factories the core needs from the loader layer (empty NBT, an inventory, an item stack), implemented by `sliminterfacefabric.InterfaceCore` and installed in `InterfaceManager` before packs load.
- `enum Rule` — The core's switches; the interface maps them to gamerules (`RuleRegistry`) and answers them through `IWrapperWorld#rule`.

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/InterfaceManager.java

- `public static IInterfaceCore coreInterface;` — Set by `SlimVehicle#onInitialize` before anything else.

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/SyncedValue.java

- `class SyncedValue<T>` — A value the server sends to clients, declared by the core as a static field of an entity level (INT, FLOAT, STRING or NBT, with its initial value). The interface defines one `SynchedEntityData` accessor per value (`BuilderVehicle`), so the network format and its ids stay vanilla's.
- `index = ALL.size();` — Values are numbered in creation order, which is class initialisation order: superclass levels first, then each level's fields top to bottom. `BuilderVehicle` defines its accessors in this order, so the accessor ids are the same as when these fields were vanilla `EntityDataAccessor`s on the entity itself.
- `public static List<SyncedValue<?>> of(Class<?> entity)` — Every value of an entity class and its superclasses. Initialises the classes first: a level whose class was not loaded yet would otherwise be missing from the list.
- `public static SyncedValue<IWrapperNBT> nbt(Class<?> owner)` — An NBT value starts as an empty compound, created by the interface (`BuilderVehicle#initial`).

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/IEntityData.java

- `interface IEntityData` — Get and set of synced values; the entity's host implements it over its `SynchedEntityData`. Setting a value marks it dirty and sends it like vanilla data, and `AEntityA_Base#onSyncedDataUpdated` reports changes on both sides.

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/IWrapperWorld.java

- `interface IWrapperWorld` — What the core asks of a level: side, time, rules, block collision and rays, entities in a box, the blocks cars break, sounds, particles, damage sources and creating vehicles. `WrapperWorld` implements it and is cached on the level (`LevelMixin`).
- `enum Particle` — Server particles the core spawns: glass shards, crash smoke, sparks. Mapped to vanilla particle types by the interface.
- `enum DamageKind` — Damage the core deals: `RUN_OVER` (the mod's damage type, attributed to the driver so PvP rules apply), `CRASH` and `FALL` for occupants.
- `enum Access` — What a player does with a vehicle that a protected area may forbid: board it, drive it (engine and controls), place it, open its storage, damage it. The interface maps each to a zone rule of the server's protection mod (`MosvegasZones`).
- `boolean allows(IWrapperPlayer player, Access access, AEntityA_Base vehicle)` — Whether the zones under the vehicle's whole box allow it. `player` is null for damage without a player. Always true without a protection mod and on the client. Asked on the server before anything changes: boarding, placing, opening a storage, taking damage, and by `driveAllowed` while driving.
- `float breakableHardness(BlockPosition pos)` — The hardness of a block in `slimvehicle:car_breakable`, or a negative number for a block that is not in the tag or that a zone protects from terrain changes (`WrapperWorld`): cars knock down only tagged blocks where that is allowed.
- `List<AEntityA_Base> coreEntities(BoundingBox box)` — Core entities (other vehicles) whose hosts are in the box, for vehicle-to-vehicle collisions.
- `void playSound(Point3D at, String sound, float volume, float pitch)` — A sound by id, vanilla (`minecraft:…`) or from a pack's `sounds.json` (`SoundRegistry#event`).

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/IWrapperEntity.java

- `interface IWrapperEntity` — Any entity as the core sees it: the vehicle's own host as well as players, mobs and projectiles near it. Methods mirror the vanilla calls the core made before the split, under the same meaning (`position(partial)` is `getPosition`, `setOldPosAndRot`, `markHurt` is `hurtMarked`, `markImpulse` is `hasImpulse`).
- `AEntityA_Base vehicle();` — The core entity this one rides, or null when it rides nothing or a vanilla entity.
- `boolean startRiding(AEntityA_Base vehicle);` — Boards a core entity through its host, so vanilla riding rules (and `BuilderVehicle#canAddPassenger`) apply.
- `IWrapperPlayer asPlayer();` — The same entity as a player, or null.

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/IWrapperPlayer.java

- `boolean instabuild();` — Creative building rights (`Abilities#instabuild`); `isCreative` is the game mode. Repairs and refuels without using up items check the first.
- `void openStorage(IWrapperInventory inventory, int rows, Text title, Predicate<IWrapperPlayer> stillValid);` — A chest screen on the inventory; stillValid is asked every tick and closes the screen when the player walks away or the lid shuts.

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/IWrapperNBT.java

- `interface IWrapperNBT` — A compound tag. The keys the core writes are the same as before the split, so worlds saved by 0.2.3 load unchanged (`DamageGameTests#worldsSavedBeforeTheCoreSplitLoadUnchanged`).

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/IWrapperInventory.java

- `List<IWrapperItemStack> moveTo(IWrapperInventory target);` — Moves the items into another inventory slot by slot and returns what did not fit, which the vehicle drops (`AEntityF_Multipart#storage` after a resize).
- `void load(IWrapperNBT tag, String key);` — Items are kept as `SimpleContainer`'s list of stacks under key, the format storages were saved in before the split.

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/IWrapperDamage.java

- `interface IWrapperDamage` — An incoming damage source: the projectile (a bullet, an arrow), whether it is an explosion and where it came from, and the entity responsible.

## slimcore/src/main/java/studio/merami/slimcore/sliminterface/BlockHit.java

- `record BlockHit(boolean hit, boolean top, Point3D location)` — A ray against blocks: whether it hit, whether the face hit is a top face, and where.

## slimcore/src/main/java/studio/merami/slimcore/rendering/ModelGeometry.java

- `class ModelGeometry` — A Bedrock `.geo.json` model: bones with their pivots, rest rotations and cubes, and each cube's six quads with UVs. Read once per model from the packs (`PackParser#openAsset`) for the renderer and the code animations. The server's hull keeps its own reader (`ModelSkeleton`, block units and cube centres), which the collision tests are tuned against. Every convention follows GeckoLib 4.4.4, which drew the models before, and is checked against it vertex by vertex (positions, UVs, normals): the same model looks the same.
- `public enum Face` — Quad order of a cube; `DentMesh` and the renderer rely on it.
- `Bone bone = new Bone(index, name, parent, (float) -pivot[0], (float) pivot[1], (float) pivot[2],` — Bedrock X runs the other way: pivots negate X, and rest rotations (radians) negate X and Y.
- `double originX = -(origin[0] + size[0]) / 16.0;` — Cube corners in model units (1/16 block), X mirrored like the pivots.
- `double inflate = raw.has("inflate") ? …` — A cube's own `inflate` wins over its bone's; both are in model pixels.
- `double x = Math.floor(size[0]);` — Box UV lays the faces out from the floored size, as Blockbench and GeckoLib do.
- `if (!mirror) {` — GeckoLib swaps the U edges of every quad that is not mirrored and flips the X normal of mirrored ones; done the same here so the textures land where they did.
- `case UP -> mirror && !boxUv ? …` — Per-face UV on a mirrored cube swaps the corner order of the top and bottom faces.
- `public static synchronized void clearCache()` — Called on a client resource reload (`VehicleRenderer#registerReload`), so a model edited in a pack folder shows after F3+T without restarting the game.
- `while (!pending.isEmpty()) {` — Bones may list a parent that comes later in the file; they are placed once their parent is. A parent that never comes is a pack error.

## slimcore/src/main/java/studio/merami/slimcore/rendering/AnimationFile.java

- `class AnimationFile` — A Bedrock `.animation.json`: per clip and bone the rotation, position and scale channels, each a list of keyframes per axis. Times are kept in ticks.
- `public record Keyframe(double length, MoLang.Value start, MoLang.Value end, Easing easing, Double argument)` — A segment from the previous keyframe's value to this one's, over length ticks, with this keyframe's easing (as GeckoLib does: the easing of the keyframe a segment ends at).
- `if (rotation && value.constant()) value = …` — Rotations are degrees in the file; constants are turned into radians at load (X and Y negated like the bone rotations), expressions when evaluated (`Channel#convert`).
- `if (raw.has("animation_length")) length = …` — A clip lasts `animation_length` if set, else until its last keyframe; a clip without keyframes never ends.
- `if (frame.has("pre")) …` — A `pre`/`post` keyframe is two keyframes 1e-7 s apart, an instant jump.
- `if (element instanceof JsonPrimitive primitive) {` — A single number stands for the same value on all three axes, an array for a constant channel.
- `public double first(Axis axis, MoLang.Context context)` — The value the clip starts with, the target of a transition into it (`Animator#transition`).

## slimcore/src/main/java/studio/merami/slimcore/rendering/Easing.java

- `class Easing` — Easing curves by their Bedrock/GeckoLib names, unknown names falling back to linear. The curves are GeckoLib's, quirks included, so animations made for it play the same: `easeinquint` uses the fourth power, `catmullrom` is GeckoLib's formula, elastic, back and bounce take the optional `easingArgs` value.
- `if (tick >= length) return (float) to;` — At or past the end the target is returned rounded to float, exactly like GeckoLib's interpolation.

## slimcore/src/main/java/studio/merami/slimcore/rendering/MoLang.java

- `class MoLang` — A small MoLang for keyframe values: numbers, `+ - * /`, brackets, unary minus, `query.anim_time` (seconds into the clip) and `query.life_time` (seconds the vehicle has existed), and the `math.` functions listed in `name()`. Trigonometry takes degrees, as in Bedrock. Anything else is a parse error with the expression and position, which skips the pack's vehicle at load instead of misbehaving in game.
- `public interface Value` — A parsed value; `constant()` tells numbers from expressions, so constants are converted once at load.

## slimcore/src/main/java/studio/merami/slimcore/rendering/Animator.java

- `class Animator` — Plays a definition's JSON animations on its model, one controller per entry in `animations`, and keeps the resulting `Pose`. One per entity (`AEntityD_Definable#animator`), updated by the renderer every frame with the time in ticks.
- `public static final int ROTATION = 0;` — Each bone has nine channels: rotation (radians), position (model units) and scale, three axes each.
- `if (!condition.test(controller.entry.when)) {` — A controller plays while its condition holds (`AEntityC_Renderable#animationActive`) and stops when it fails; the next time it starts from the beginning.
- `if (controller.entry.transition > 0) {` — With a transition, the bones first ease linearly from where they are to the clip's first frame over `transition` ticks.
- `if (controller.entry.loop) {` — A looping clip starts over at its end; a play-and-hold one keeps its last frame (`Channel#value` past the end).
- `pose.values[bone][index] = (float) value + (channel == 0 ? pose.bind[bone][index] : 0);` — Animated rotation is added to the bone's rest rotation; position and scale replace the rest values. Same as GeckoLib.
- `double progress = Math.min((time - resetStart[bone][channel]) / RESET_TICKS, 1);` — A channel no controller drives any more returns to its rest value over one tick (GeckoLib's reset), so a mirror that stopped folding does not stay frozen half-way.
- `public static final class Pose` — Rest values (`bind`), current values and visibility per bone. The renderer copies it per frame (`VehicleRenderer`) and code animations (`VehicleAnimations`) write into that copy, so they never feed back into the animator.
- `public void setHidden(int bone, boolean value)` — Hiding a bone hides everything under it too (`hideChildren`), as GeckoLib's `setHidden` does.

## slimcore/src/main/java/studio/merami/slimcore/jsondefs/AJSONItem.java

- `abstract class AJSONItem` — Base of every definition read from a pack: name, spawn item, model, texture, animation file and JSON animations. Gson fills the public fields from the merged JSON (snake_case keys, `JSONParser`); `transient` fields are set by `PackParser` and `bake()` and never read from JSON. Field initializers are the defaults, so every definition class needs its implicit no-argument constructor.
- `ResourceId EMPTY_ANIMATION = SlimCore.id("animations/empty.animation.json")` — A definition without an animation file points here, so `animationLocation` is never null; JSON animations against it fail `PackParser#checkAnimations` with the animation's name.
- `public static class Animation` — An `Animator` controller from JSON: the clip `name`, the condition `when` (`AEntityC_Renderable#animationActive`), looping or play-and-hold, and the transition length in ticks.
- `public ResourceId qualify(String location)` — A name without a namespace belongs to the definition's own pack namespace.
- `public Text displayName()` — The vehicle's name: the item's translation (falling back to the file name), or `label` as a translation key that falls back to itself as literal text.
- `public static Text text(String keyOrText)` — Labels in definitions are translation keys or plain text; both display.

## slimcore/src/main/java/studio/merami/slimcore/jsondefs/AJSONInteractable.java

- `abstract class AJSONInteractable` — Definition level of `AEntityE_Interactable`: size, mass, collision hull and the damage layout.
- `double REFERENCE_MASS = 1400` — Mass (kg) of the reference car; `massRatio` is a vehicle's mass relative to it and splits collision impulses. The Volga weighs exactly this, so its collisions are unchanged from the relative masses of 0.1.9.
- `int MAX_ZONES = 16` — Two synced ints of eight 4-bit zones (`AEntityE_Interactable` `ZONES`).
- `public static class Hull` — Plain body boxes, and model bones whose cubes join the hull: an empty index list takes all cubes, otherwise only the listed ones. Openings and glass are added from their bones anyway.
- `public static class Damage` — Damage layout of one vehicle. `half_width`/`half_length` default to the definition's size.
- `public static class Zone` — A stretch of one side of the body. from..to runs along the side: Z for the flanks, X for the ends and the roof. What a single hit there breaks: the opening it jams and the pane it shatters (at jamAt / glassAt condition lost), the engine's share of it, the steering pull of a bent wheel arch (+1 pulls left) and whether a crumpled zone leaks fuel. `jam` and `glass` name an opening id and a glass bone; `AJSONMultipart#bake` resolves them to indices.
- `public transient double bodyHalfWidth;` — Half width and half length of the body (the hull boxes), chassis units. The size is wider (mirrors, open doors), so with it a player standing beside the car counted as inside (`AEntityE_Interactable#embeds`).

### AJSONInteractable.Damage

- `public DamageModel.Side side(Point3D local)` — Side of the body a chassis-space point lies on, relative to the body's half width and length; a point over the roof line (an explosion above) is on the roof.
- `public Zone zone(Point3D local)` — Zone a chassis-space point falls in, the nearest of its side if none covers it, or null if the vehicle has no zones on that side.

## slimcore/src/main/java/studio/merami/slimcore/jsondefs/AJSONMultipart.java

- `abstract class AJSONMultipart` — Definition level of `AEntityF_Multipart`: seats, openings, glass, wheels and the sounds the parts make. `bake()` gives every part its index (its bit in the synced flags) and resolves names between them.
- `int MAX_SEATS = 16` — Limits of the synced slots: seats (rider ids, window bits), wheels (suspension heights, tyre bits), openings (open and jammed bits) and panes (broken bits).
- `public enum OpeningType` — What an opening is for: doors take riders and close on the move, a fuel flap takes the fuel can, a glovebox stays open and usable while driving, bonnets and trunks sound like hoods. Inferred from the id when `type` is missing.
- `public static class Seat` — door: the opening that must be open to get in, or null (climb on). stand: the rider stands on pos instead of sitting on a cushion there.
- `public static class Opening` — A hinged part. rotate: bone rotation (radians, model axes) when fully open. buckle: extra rotation the same way when a crash has bent it. solid parts are in the collision hull, the others can only be clicked. maxHalfWidth (0 = none) trims the click box of a door that swings past the body side.
- `public static class Storage` — Inventory behind an opening. seats: seats it can be reached from (empty: only from outside). `title` is a translation key or text with `%s` for the vehicle name.
- `public static class Glass` — A pane. window: seat whose lowering window it is, or -1 for a fixed one.
- `public static class Wheel` — A wheel; roll and contact radius default to the radius. The shared `wheel` object of a definition is copied into each entry before Gson (`JSONParser#applyWheelTemplate`).
- `public transient double axleSpan;` — Distance between the mean front and rear axle, chassis units.
- `public transient double track;` — Distance between the left and right wheels (0 for a single track), chassis units.
- `public String openingSound(Opening opening, boolean opens)` — An opening's own sound, else the sound of its kind from `sounds`.

## slimcore/src/main/java/studio/merami/slimcore/jsondefs/JSONSounds.java

- `class JSONSounds` — Sound events of a vehicle, as `namespace:path` of an event in a resource pack's `sounds.json`. Defaults are the built-in sounds; `AJSONMultipart#bake` qualifies names without a namespace with the pack's.
- `public String startInside;` — The start sound heard inside the closed cabin (`CabinAcoustics`); unset, the outside `start` plays through the cabin's filter. Each client plays the start sound itself (`VehicleEffects#start`) from the server's `START_TICK`, so the catch, the loops and the smoke all count from the same moment.
- `public double startCatch = .2;` — Seconds into the start sound at which the engine fires: the engine loops begin to fade in and the tailpipes throw their first cloud and flame. Measured on the recordings: the Volga's AMC I4 catches at 0.2 s, the Ford FE 428 after 0.27 s of cranking, at 0.3 s.
- `public double startSettle = .8;` — Seconds into the start sound by which it has settled to idle; the engine loops are full from here. Before, the loops played from the first tick under the start sound and doubled its rev flare.
- `public double spinDown;` — Seconds the engine loops take to wind down (revs, pitch and volume falling together) after the engine stops; 0 cuts them at once. A car without a stop recording of its own (`"stop": ""`) winds down instead of borrowing another engine's.
- `public List<Integer> popTicks = new ArrayList<>(List.of(1, 3, 5, 6, 12));` — Ticks after a pop event at which the tailpipes flash: the bangs of the built-in `exhaust_pop` (the AMC I4 sputter) come at 0.05, 0.15, 0.25, 0.33 and 0.6 s. A pack with another pop sound lists its own bangs.
- `public float startFade(int ticks)` — Share of the engine loops' volume `ticks` after a start: none until the catch, a smoothstep up to the settle, full after (also for an engine that was running before the client saw it).
- `public static boolean silent(String sound)` — An empty name (`""`) is a sound left out on purpose: nothing plays and the name is not qualified. Everything that plays vehicle sounds asks this first.
- `public String gearShiftDown;` — Lever going down (towards reverse); unset uses `gear_shift`, so a car with one shift sound keeps it for both ways.
- `public String trunkOpen;` — Trunk sounds; unset uses the bonnet's. A side-hinged tailgate like the G 63's sounds like a door, not a lid.
- `public static class EngineLayer` — One engine loop. It plays at full volume between the end of `fade_in` and the start of `fade_out` (rpm), fading linearly over each range, and its pitch rises from `pitch_rpm`. The three default layers reproduce the 0.1.9 low/mid/high mix exactly.

### JSONSounds.EngineLayer

- `public double pitchFollow = Double.NaN;` — How closely the pitch follows the revs: 1 plays the loop at rpm / `pitch_rpm`, so `pitch_rpm` is the revs it was recorded at (Immersive Vehicles layers its engines this way); 0.5 follows half as steeply. Unset keeps the gentle 0.1.9 curve the default loops were tuned to.
- `public float pitch(float rpm)` — Minecraft plays pitches between 0.5 and 2 only; layers fade out before they reach either end.
- `public String inside;` — The same loop recorded in the cabin (Immersive Vehicles ships `_inside` versions). `VehicleAudio` crossfades to it while the camera is in this car's closed cabin; a layer without one goes through the cabin's low-pass filter instead.

## slimcore/src/main/java/studio/merami/slimcore/jsondefs/JSONVehicle.java

- `class JSONVehicle` — One vehicle as data. Physics, collision, damage, animation, lights, effects and controls are the same code for every vehicle and take their numbers from here, so a new vehicle is a definition plus its model, not new code. Positions are chassis units (+X driver's left, +Y up, +Z forward, origin on the ground under the centre); the world size is `scale` times that.
- `int MAX_GEARS = 9` — Forward gears a transmission may have.
- `public double maxStep = .625;` — Highest kerb or slab it drives onto; 0 never climbs.
- `public double bodyPivot = .6;` — Height of the point the body pitches and rolls about.
- `public static class Physics` — Top speed (km/h), acceleration (blocks/tick², NaN: from engine power and mass), brake, grip, road-wheel angle at full lock (radians) and how much speed reduces it, and the wheelbase used for turning (NaN: the axle span).
- `public static class Transmission` — Number of gears or the top speed of each (km/h), reverse speed and torque ratios. Without explicit speeds the gears spread from the first (about 23 % of top speed in a five-speed box) to the top speed; without ratios they follow the speeds. `automatic` lets `AEntityVehicleE_Powered#tickTransmission` pick the forward gear; `shift_up` is the share of a gear's reachable speed at which it changes up, `shift_down` the share of the lower gear's speed below which it changes back.
- `public List<Point3D> exhaustPipes = new ArrayList<>();` — Tailpipes besides `exhaust` (a pair of side pipes, a quad exhaust). The exhaust pop sound stays at `exhaust`.
- `public Point3D exhaustDirection = new Point3D(0, 0, -1);` — Where the pipes blow, in chassis axes; backwards by default. Side pipes in front of a rear wheel blow outwards, or the smoke goes straight into the tyre.
- `public boolean exhaustMirror;` — Every pipe again at the mirrored X, blowing the mirrored way (side pipes on both flanks, twin exhausts).
- `public transient List<Tailpipe> tailpipes;` — Every pipe with its direction, mirrored ones included; `VehicleEffects#exhaust` smokes each of them.
- `public ExhaustEffects exhaustEffects = new ExhaustEffects();` — What the tailpipes show (`VehicleEffects#exhaust`): `smoke` scales the running smoke, `start_smoke` and `start_seconds` the cloud that rolls out when the engine catches, `start_flame` a flame at the catch. `overrun` pops when the throttle is lifted at high revs, `shift` on full-throttle upshifts of an automatic (an AMG's crackle); `flames` shows the pops at the pipes.
- `public Cabin cabin = new Cabin();` — The box (chassis units) the camera must be in for the cabin to muffle the outside, and how strongly (0..1). Unset: the seats' extent widened by `CABIN_SIDE` and `CABIN_LENGTH`, from 0.3 below the seats to `CABIN_HEAD` above them. A vehicle without doors (the scooter) does not muffle.
- `public static final double DEFAULT_MUFFLE = .85;` — In the closed cabin of the reference video the engine kept its loudness and lost about three quarters of its high end; with `CabinAcoustics`' gains this comes out at that.
- `public boolean inCabin(Point3D local)` — Whether a chassis point is in the cabin box; never for a vehicle that does not muffle.
- `public static class Engine` — Where a damaged engine smokes and burns first (none without `pos`), the bone shaken by the running engine, power (kW) for the derived acceleration, and the revs: idle, at a gear's speed limit, the cap, the free rev under throttle and the start of the tachometer's red zone. `keep_running`: seconds the engine idles on after the driver gets out (`AEntityVehicleE_Powered#tickParkedEngine`), 120 by default; 0 switches everything off at the exit as before.
- `public static class Lamps` — Lens centres (left side, mirrored for the right), the lamp bones and the headlight type.
- `public static class LampBone` — Cube order of a lamp bone: `left[i]` is true for the left lamp.
- `public List<Glow> glow = new ArrayList<>();` — Extra lamps that light up only where their texture is lamp-red (`VehicleRenderer`'s glow pass): a third brake light, red strips on a bumper. `when` is `tail` (with the lights, brighter when braking) or `brake` (only when braking); anything else is refused at load.
- `public String headlightType = HEADLIGHT_HALOGEN;` — `halogen` (warm, yellowish) or `led` (cold white, the G 63); anything else is refused at load. A player can force one type for every car in `slimvehicle-client.properties` (`ClientConfig#led`).
- `public String drive = DRIVE_REAR;` — Driven wheels: `rear` (default), `front` or `all`. All-wheel drive puts the whole grip down at a launch (`DrivePhysics#traction`); two driven wheels only their axle's share.
- `public static class Emitter` — Smoke and fire source: a point and how far around it puffs and flames spread.
- `public static class Steering` — The wheel the driver holds: hub centre, column tilt, offset of the rim plane, rim radius and how many radians the wheel turns per radian of road-wheel steering.
- `public Point3D axis = new Point3D(-1, 0, 0);` — Local axis of the steering bone that the wheel spins about, signed so that a positive steer (left) turns it anticlockwise for the driver. The Volga's bone sits in a frame whose -X is the column; a Blockbench wheel modelled facing Z in a tilted parent spins about +Z.
- `public static class Render` — Chassis bone; bones tinted with the paint colour; body bones (with everything under them) that dents bend, while glass, wheels and the cabin stay as they are; cabin detail skipped for distant vehicles; shadow half extents; top of the speedometer scale (km/h, NaN: from the top speed).

### JSONVehicle.Steering

- `public Point3D grip(boolean left, float steer)` — Where one hand holds the rim. Hands slide and regrip on their own half at large lock, so the wheel can turn past 180 degrees without taking the arms across the hub.

### JSONVehicle

- `private List<Tailpipe> tailpipes()` — `exhaust` first, each pipe followed by its mirror; pipes without `exhaust` are an error rather than silently dropped.
- `private double acceleration()` — Explicit acceleration wins. Otherwise 74 kW in 1400 kg gives the Volga's tuned 0.0135; more mass slows it, more power speeds it up.
- `private DrivePhysics.Gearbox gearbox()` — Index 0 is reverse, 1 neutral, 2.. the forward gears. Neutral keeps the full speed limit and no torque. Reverse ratios are stored negative.

### JSONVehicle.gearbox()

- `if (!(transmission.shiftDown > 0 && …` — With the lower point at or above the upper one the automatic would change up and straight back down.
- `: 3.5 * Math.pow(fractions[2] / fractions[gear + 1], .9);` — First gear pulls hardest; longer gears pull less, roughly in proportion to their speed. With the default 0.23 first gear this lands close to the hand-tuned 3.5/2.2/1.45/1.0/0.82 of the Volga.

## slimcore/src/main/java/studio/merami/slimcore/jsondefs/JSONPack.java

- `class JSONPack` — `slimpack.json`: a pack's name, description and the order of its vehicles (the rest follow alphabetically). A mod is a pack only if it has this file; folder packs may omit it.

## slimcore/src/main/java/studio/merami/slimcore/packloading/JSONParser.java

- `class JSONParser` — Gson set up for pack JSON: snake_case keys, `Point3D` as `[x, y, z]`.
- `public static JsonObject merge(JsonObject base, JsonObject over)` — Objects merge key by key, anything else is replaced. Used for `parent` and for config overrides.
- `public static void applyWheelTemplate(JsonObject vehicle)` — Copies the shared `wheel` object into every wheel that does not set the key itself, then drops it, so `Wheel` needs no "unset" markers.
- `public static <T> T parse(JsonObject json, Class<T> type, List<String> unknownKeys)` — Gson silently ignores keys that match no field, so a typo would just do nothing. Every key is checked against the fields of the class it lands in (lists and maps by their element type) and the unknown ones are returned with their path.

## slimcore/src/main/java/studio/merami/slimcore/packloading/PackParser.java

- `class PackParser` — Finds vehicles in pack sources, resolves `parent`, applies the vehicle configs (`VehicleConfig`) and the old overrides and the wheel template, maps them with Gson and bakes them. Vehicles are found under `assets/<namespace>/vehicles/*.json`; their id is `namespace:file name`. Runs at mod initialisation on both sides: items are registered per vehicle, so the definitions must be known before registries freeze. A client joining a server builds them again with the server's configs (`useRemoteConfigs`) and with its own after leaving; the ids stay, so items and registries are untouched.
- `public static synchronized void load(List<Source> sources, Path configs, Path overrides)` — configs: the folder of per-vehicle configs (`config/slimvehicle/vehicles`), null for none (tests, tools); overrides: the older admin overrides, applied after them.
- `private static JSONVehicle build(String id, Source source, JsonObject resolved, Path overrides)` — The definition parsed once as the pack gives it, for the config's defaults (`VehicleConfig#defaults`), then built with the config merged in. A config value that does not fit (a drive that does not exist, gears in the wrong order) is logged and the pack's own values are used, so an edited file never stops a server.
- `public static synchronized Map<String, JsonObject> configs()` — The configs in force, by vehicle id, as the server sends them to joining players (`PacketVehicleConfigs`).
- `public record Source(String name, List<Path> roots, boolean builtIn)` — A pack: a mod's root paths (classes and resources may be separate roots in development), a folder or a zip file system. A broken definition stops the game only in the built-in pack; in other packs it is logged and skipped.
- `private static List<String> ordered(JSONPack meta, List<String> found)` — The pack's listed order first (by full id or file name), then the rest as found (sorted).
- `private static JsonObject resolve(String id, Map<String, JsonObject> raw, Map<String, JsonObject> done, Set<String> seen)` — The definition with its parent chain merged in; a parent without namespace is looked up in the child's.
- `public static synchronized JSONVehicle vehicle(String id)` — The definition with this id (full or by file name), or the first loaded one for an unknown id: an entity whose pack was removed still loads as a default car instead of crashing.
- `public static synchronized Optional<Path> findAsset(ResourceId location)` — A file of any pack under `assets/`. Models and animations are read this way on both sides (`openAsset`): the server has no resource manager, and the client draws exactly what the server collides with.
- `private static void checkAnimations(JSONVehicle vehicle)` — Every animation named in the definition must be in its animation file, and the file must parse (MoLang included): a typo is a pack error at load, not an animation that silently never plays.

## slimcore/src/main/java/studio/merami/slimcore/packloading/VehicleConfig.java

- `public final class VehicleConfig` — One file per vehicle in `config/slimvehicle/vehicles/<namespace>/<name>.json`, written on start with the vehicle's current values, so a server owner finds every car and what can be tuned (the mod had no folder of its own to edit before). Edits apply on the next start.
- `public static final List<String> FIELDS = List.of(` — What the file offers: mass, the drive (top speed, acceleration, brakes, grip, steering, driven wheels), the gearbox, the engine (power and revs, how long it idles after the driver leaves), the tank and the speedometer scale. A value the pack leaves to the mod (NaN: acceleration from power, the speedometer from top speed) is not written, so it keeps following; written in, it is fixed.
- `public static JsonObject local(Path folder, String id, JsonObject defaults)` — Edits survive mod updates without freezing what the admin never touched: next to each file a copy of the defaults it was written with is kept (`.defaults/<namespace>/<name>.json`). On start a value that differs from that copy is the admin's and stays; every other value follows the new defaults; the file is rewritten to show what is in force. A file without the copy counts as all edited.
- `public static JsonObject rebase(JsonObject user, JsonObject before, JsonObject defaults)` — The three-way merge behind `local`, by dotted path.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/ChassisGeometry.java

- `class ChassisGeometry` — Pure chassis geometry and rider scales. Chassis space: +X driver's left, +Y up, +Z forward; entity transforms are on `AEntityB_Existing` and `AEntityVehicleD_Moving`.
- `float RIDER_SCALE = .70f` — Seated riders are drawn smaller than standing players so they fit the cabin.
- `public static float seatedEyes(AJSONMultipart definition)` — Eye height of a seated rider above the entity position.
- `double PUNCTURE_DROP = .105` — A flat tyre keeps its tread on the road; the rim (and that body corner) sinks by the lost sidewall.
- `public record Pose(double pitch, double roll, double heave)` — Body pitch/roll/heave in chassis units; same convention as bodyPitch/bodyRoll/bodyHeave.
- `public static Pose wheelPlane(AJSONMultipart definition, double[] offsets)` — Plane through the wheel-centre offsets (+ is up), i.e. where the body has to sit: pitch from the mean front and rear wheel, roll from the mean left and right one (none for a single track).
- `public static boolean overlaps(Point3D center, float yaw, double halfWidth, double halfLength, double low, double high, BoundingBox box)` — SAT for a yaw-oriented vehicle rectangle and an axis-aligned obstacle.
- `public static Point3D toLocal(Point3D world, Point3D position, float yaw, double scale)` — A world point in the chassis frame of a car standing at `position` with `yaw` (degrees); `AEntityB_Existing#worldToLocal` for the current tick and for a frame.
- `public static Point3D levelled(Point3D local, double bodyPitch, double leanPitch, double roll, float yaw)` — A chassis direction in world axes with the body's pose minus its inertial pitch (`leanPitch`): self-levelling headlights. Squat at a launch and dive under braking leave the beam on the road; the road's own slope, bumps and the body's roll still tilt it. Without it a G 63 pulling away lifted its beam two degrees, where the dipped beam's 1/t³ patch moves almost entirely above the horizon, and the road went dark.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/PoseReplay.java

- `class PoseReplay` — Client side of a car's motion. The server stamps a pose (position and heading) with its tick whenever the car has moved, but the packets reach the client unevenly: none in one client tick, two in the next, a burst after a hitch. Gliding a third of the way towards whatever arrived last, as before, made the car and the view of everyone in it speed up and slow down from tick to tick, which showed as jerks in every turn. Here the poses are replayed tick by tick a fixed delay behind the newest one, so a late packet is absorbed and the motion keeps the server's even pace.

### PoseReplay

- `double DELAY = 2` — Ticks the replay stays behind the newest pose: packets up to two ticks late still arrive in time.
- `double MAX_DELAY = 8` — Further behind than this (a long hitch) the replay jumps ahead instead of hurrying.
- `double TELEPORT = 64` — A pose farther than this (blocks) from the last one is a teleport: shown at once, never glided to.
- `double SMOOTH = .1` — Pace control: smoothing of the measured delay (SMOOTH), the pace's response to it (GAIN), and how fast the base pace follows a server that ticks slower or faster than the client (DRIFT).
- `int QUIET = 2` — Ticks without a new pose after which the car counts as standing still rather than late.
- `private double time;` — Replay clock in server ticks; behind is its smoothed distance behind the newest pose, pace the speed it runs at and base the long-term part of that pace.
- `public void add(int tick, Pose pose, Pose shown)` — A pose the server stamped at a tick. shown is the pose the car shows now, where the first one starts.
- `public Pose next()` — Advances the replay by one client tick: the pose to show, or null before the first one has arrived.

### PoseReplay.add(int,Pose,Pose)

- `if (tick <= newest || distance(last, pose) > TELEPORT) clear();` — A server that counts from scratch, or a car moved far away: start over.
- `else if (tick - newest > 1) push(tick - 1, last);` — The server sends nothing while the car stands still, so it stood where it was until the tick before.

### PoseReplay.next()

- `} else if (!flowing) {` — Standing still: keep the server's pace, so moving off continues seamlessly.
- `if (lag < 0) {` — Ahead of the server (it ticks slower): wait for it rather than run past the newest pose.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/DamageModel.java

- `class DamageModel` — Crash damage rules as plain numbers; the vehicle applies them to its synced state. Speeds are blocks/tick (×72 for km/h); condition runs from 100 (new) to 0 (wrecked).

### DamageModel

- `double IMPACT_THRESHOLD = .12` — Speed change a car absorbs without damage, about 9 km/h.
- `float LAMP = 3` — Single-impact strength (condition lost) that breaks a lamp; TYRE and WHEEL: a tyre hit directly or close by.
- `float ENGINE_FIRE = 10` — Engine condition below which it burns; a dead engine burns harder and a wrecked car burns whole.
- `double PULL = .035` — Steering offset (radians) of a fully crushed wheel arch.
- `public static float impact(double deltaV)` — Condition lost when the car's speed changes this much in a collision. Squared like the crash energy: head-on into a wall about 10 at 30 km/h, 35 at 50 km/h and a wreck at 100 km/h.
- `public static float landing(double speed)` — Condition lost landing at a vertical speed: nothing from two or three blocks, about 25 from ten.
- `public static float occupant(double deltaV)` — Health an occupant loses in a collision, from a speed change of about 36 km/h.
- `public static float runOver(double closing)` — Health a pedestrian loses when the car closes on them at this speed: from about 14 km/h, some 14 at 50 km/h and 30 at 100 km/h.
- `public static float scrape(double slide)` — Wear while grinding along a wall, per tick of contact.
- `public static double power(float engine)` — Engine output left: unaffected down to 60 %, then falling to about a third.
- `public static float fire(float engine, float body)` — Fire strength 0..1 from the condition: nothing, flames licking from the engine bay, a burning engine, then the whole car.
- `public record Dent(Point3D pos, Point3D dir, float depth, float radius)` — A dent in the body: centre on the surface and the direction it was pushed in (chassis units), how deep at the centre and how far it spreads. The renderer bends the model's surface around it.
- `public static float dentDepth(float amount)` — Dent depth and spread for a hit costing this much condition: none below 1.5, about 0.14 deep at 30 km/h head-on and the maximum from 50 km/h.
- `public static boolean dent(List<Dent> dents, Point3D pos, Point3D dir, float amount)` — Adds a hit to the dents: one close to an earlier dent deepens and widens it, otherwise it is a new dent (the shallowest one goes when there are too many). Returns false if the hit leaves no mark.
- `public static float falloff(Dent dent, Point3D point)` — Deepest dent weight at a chassis point: 1 at a dent centre, 0 outside every dent.
- `public static Point3D displacement(List<Dent> dents, Point3D point)` — How far the dents push a chassis point of the body (chassis units): along each dent's direction by its depth where it reaches, a bit more or less from place to place, and sideways a little where the metal folds. The bent mesh (`DentMesh`), the hinges of dented parts (`VehicleRenderer#hinge`) and the hull (`AEntityVehicleC_Colliding#boneMatrix`) all use it.
- `private static float noise(double x, double y, double z, int seed)` — Smooth value noise in -1..1 with features about a quarter of a block across.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/Hull.java

- `class Hull` — Simplified exterior volumes of a vehicle at one position and heading: oriented boxes for the body, solid openings, panes and tyres, plus the click boxes of openings (`controls`). Interior decoration is deliberately not physical. Built by `AEntityVehicleC_Colliding#buildHull`; suspension never moves the physics hull.
- `public record Frame(Point3D center, Point3D ex, Point3D ez, double width, double length)` — The car's own axes at build time (centre on the ground, chassis X and Z as world directions) and the body's half width and length in blocks, for `#entityBoxes`.
- `public List<Box> entityBoxes()` — The boxes people and animals collide with: the hull, with every box that lies square to the car (closed doors, tyres, lids) cut back to the body's side and end planes. The tyres stuck out of the side by 6 cm and the doors by about 2 cm: walking along a car standing square to the world, a player was stopped at every wheel and door edge, a ledge vanilla movement cannot slide past. Turned boxes (open doors, panes, pillars) keep their shape. The car's own physics uses the full hull.
- `public boolean intersectsEntity(BoundingBox box)` — Whether a box overlaps `#entityBoxes`.
- `public int glassRay(Point3D start, Point3D end)` — Index of the pane the ray meets first, or -1 if it meets the body (or nothing) first. The part a pane belongs to does not hide it: a door's box is the outline of the whole door, window included, so a blow at the window would otherwise always land on the door.
- `public static Box chassisBox(Matrix4d root, Point3D local, Point3D half, APart part, int glass)` — A box at a chassis point. Model space mirrors chassis X and Z.

### Hull.Box

- `public final APart part;` — Part the box belongs to (an opening, including the glass in it, or a wheel); null is the body.
- `private List<BoundingBox> slices;` — Vanilla resolves movement against AABBs. Subdivide rotated surfaces so their empty corners never become a whole-car invisible wall.
- `private static final double SLICE = .45;` — Slices are at most SLICE long. Along an axis turned away from the world axes, a box thinner than THIN in another turned direction (an open door, a raised bonnet or lid, a pane) is cut into slices no longer than 0.75 of that thickness, and no shorter than FINE_SLICE. At 45 degrees each slice's AABB sticks out of the surface by half the slice's length, so 0.45 slices made an open door a staircase of steps about 0.2 deep.
- `public Point3D escape(BoundingBox other)` — Shortest move that takes a box out of this one: along world X or Z or this box's horizontal axes, or straight up (never down).

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/HullCollision.java

- `class HullCollision` — Narrow phase of the moving hull against block boxes and other vehicles. A move is blocked only by a new overlap or by deepening one that already exists, so a car that already touches something (a door opened against a wall, a corner left inside a fence) can always back out of it. A contact says when along the move the overlap began and on which face, so the car can stop at the surface and keep the part of its motion that runs along it instead of stopping dead.

### HullCollision

- `public record World(List<BoundingBox> blocks, List<Other> vehicles)` — Block boxes and nearby vehicles, gathered once per tick. The block list may shrink when a block breaks.
- `public record Touch(int index, Hull.Box obstacle, BoundingBox block, Other other)` — A hull box (by index) overlapping an obstacle: a block box or a box of another vehicle.
- `public record Contact(double time, Point3D normal, Point3D point, Hull.Box box, Hull.Box obstacle, BoundingBox block, Other other)` — First touch of a move: fraction of the move (0..1) when it began, horizontal unit normal pointing out of the obstacle, point on the car, the car's box and the obstacle.
- `private static final double SAME_TIME = 1e-4;` — Contacts this close in time are the same impact (a bumper meeting two blocks of one wall).
- `public static boolean blocked(Hull before, Point3D from, Hull after, Point3D to, World world)` — Whether moving the hull from before+from to after+to creates a new overlap or deepens one.
- `public static List<Touch> touches(Hull before, Point3D from, Hull after, Point3D to, World world)` — Every overlap that blocks the move (see `#blocked`).
- `private static double depth(Hull.Box box, Point3D offset, Hull.Box obstacle)` — How far a box shifted by offset is inside the obstacle: its smallest overlap, vertical included, so a box resting on a block top does not count as pushed into it when it moves along.
- `public static Contact contact(Hull hull, Point3D from, Point3D to, World world)` — Where the move from hull+from to hull+to first meets something that blocks it, or null if that cannot be told (then the caller stops as before).
- `public static Point3D separate(Hull before, Hull after, World world, double max)` — Horizontal shift that undoes the overlaps a turn (before → after, both at the origin) created, or null if that takes more than max. A corner swinging into a wall pushes the car off it rather than locking the steering.
- `private static double gap(Hull.Box box, Point3D offset, Hull.Box obstacle, Point3D axis)` — Gap between a car box shifted by offset and an obstacle along a unit axis; negative is overlap.
- `private static List<Point3D> axes(Hull.Box box, Hull.Box obstacle)` — Horizontal face directions of both boxes. Projections onto them are exact, so a gap on any of them proves the pair apart.
- `private static Point3D closest(Hull.Box box, Point3D offset, Point3D target)` — Point of the box (shifted by offset) closest to a target point.

### HullCollision.scan(Hull,Point3D,Hull,Point3D,World,List<Touch>)

- `BoundingBox local = block.move(back);` — Move the obstacle into the hull's frame instead of moving every box of the hull.

### HullCollision.contact(Hull,Point3D,Point3D,World)

- `if (g1 > least) {` — Already overlapping and pushed deeper: the face it is least inside of.
- `double t = g0 / (g0 - g1);` — The face crossed last is the one it hit.
- `Point3D normal = normals.lengthSqr() > 1e-6 ? normals.normalize() : first.normal();` — One impact over several boxes (a bumper across two blocks): its centre and mean face.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/ModelSkeleton.java

- `class ModelSkeleton` — Bones and cubes of a Bedrock model in model space (X mirrored like `ModelGeometry`), read from the pack that has the model. The server needs them for the hull and has no resource manager, so they are read through `PackParser#findAsset`.

## slimcore/src/main/java/studio/merami/slimcore/baseclasses/InteractionZone.java

- `record InteractionZone` — Something a click can use: a seat to board, an opening (its lid or, with `storage`, its contents) or a wheel. Click zones are independent of physics: furniture never obstructs movement. `label` is a translation key or text.

## slimcore/src/main/java/studio/merami/slimcore/physics/DrivePhysics.java

- `class DrivePhysics` — Deterministic 20 Hz drivetrain with real-world quantities. Speeds are blocks/tick, accelerations blocks/tick² (1 block = 1 m, so ×20 gives m/s and ×400 gives m/s²). Pull comes from engine power through the gear, limited by what the tyres can put down; air drag grows with the square of speed and, with rolling resistance and engine braking, slows the car; cornering is limited by tyre grip. The arcade model before 0.2.5 accelerated at a fixed rate per gear, let the G 63 do 0-100 km/h in 2.5 s and turned at about 3 g at 120 km/h.
- `public record Gearbox(double[] fractions, double[] ratios)` — Per gear from reverse (index 0) through neutral (1) to the top gear: the share of top speed it reaches at `limit_rpm` and its torque ratio.
- `public record Revs(double idle, double atLimit, double max, double free)` — Engine speed: idle, at a gear's speed limit, the cap, and the slip an automatic's converter adds under full throttle (`rev_rpm`).
- `Gearbox gearbox, Revs revs, double mass, boolean allWheel, boolean automatic) {}` — `mass` is the definition's mass ratio (`mass` / 1400 kg); everything that depends on weight reads it here. `allWheel` comes from `physics.drive`, `automatic` from the transmission (torque converter, weaker engine braking).
- `public static final double GRAVITY = 9.81 / 400;` — g in blocks/tick².
- `private static final double SPECIFIC_POWER = 44.93 / .0135 / 8000;` — `acceleration` (the old first-gear figure, still what JSON and mass scale) read as power at the wheels per kilogram: the Volga's 74 kW × 0.85 drivetrain efficiency / 1400 kg = 44.93 W/kg at 0.0135. The force at speed v from power P is P / v; per kilogram and in blocks/tick² that is (W/kg) / (8000 v) with v in blocks/tick.
- `private static final double ROLLING = .013 * GRAVITY;` — Rolling resistance of road tyres, 1.3 % of the weight.
- `private static final double HEADROOM = 1.06;` — Air drag is sized so that full power would balance it at 6 % over the top speed: the car really gets to `max_speed_kmh`, slowly at the end, and the top gear's governor holds it there, like an electronically limited car.
- `private static final double LAUNCH = .3;` — Engine speed share at which the clutch (or converter) holds the engine while the car is slower than that in the gear, so a launch has torque instead of dividing power by zero speed.
- `private static final double CONVERTER = .7;` — An automatic's torque converter multiplies torque by up to 1.7 at a standstill, fading out by `STALL` of the gear's speed.
- `private static final double ENGINE_BRAKE = .12;` — Engine braking with the throttle closed in gear, as a share of full torque in that gear; through a converter (`CONVERTER_BRAKE`) much less.
- `private static final double REAR_SHARE = .55;` — Share of the weight on the driven axle of a two-wheel-drive car under acceleration; all-wheel drive uses all of it.
- `private static final double SCRUB = .2;` — Front tyres turned at the steering angle drag the car back by part of the cornering force (`|a_lat| · sin(steer)`): hard cornering costs some speed. It was 0.5 while the cornering grip was the real one; with `CORNER_GRIP` that took a fifth of the speed out of every corner and the Volga could not hold 45 km/h on full lock.
- `private static final double CORNER_MARGIN = .95;` — The yaw rate a driver gets from the wheel stays just under what the tyres can hold, so a car on full lock at speed holds a wide line instead of sliding and squealing in every turn (keyboard steering is all or nothing). The handbrake and lift-off oversteer (`Drift#loss`) raise the cap, so slides stay possible.
- `private static final double BRAKE_GRIP = 1.15;` — Tyres hold more along the road than across it (ABS braking): braking is capped at 1.15 × the cornering grip.
- `private static final double CORNER_GRIP = 3.4;` — Sideways the tyres hold 3.4 × their real grip (`cornering`), about 2.6-2.8 g: game handling, not a road car's. With the real 0.8 g a G 63 on full lock needed a 37-block circle at 60 km/h and 103 blocks at 100 km/h, and from rest with W and A held it took 3.6 s to turn 90°, so players found the cars impossible to steer in a town. Now (handling sim, full lock held): 6 blocks at 10 km/h, 8 at 30, 11.5 at 60, 30 at 100, and 90° in 2 s from rest. Only cornering uses it: launch, braking and stopping distances keep the real grip. Before 0.2.8 braking in a turn also loosened the rear (from 18 km/h, later from 61 km/h) and full throttle in any turn above 22 km/h started a power slide; with keyboard pedals at full, nearly every turn slid (26–50° in the sim). Only the handbrake slides the car now.
- `private static final double NEUTRAL_REVS = .75;` — Engine speeds as shares of `limit_rpm`: free revving in neutral reaches `NEUTRAL_REVS` at full throttle; pulling away, an automatic's converter holds the engine at up to `STALL_REVS` and a slipping clutch at `CLUTCH_REVS`.
- `private static final double REV_RISE = 260;` — How fast the shown revs follow (`revsToward`): up to 260 rpm a tick up and 380 down, so a shift sweeps the needle and the sound instead of jumping.
- `public static double weight(double mass, double exponent)` — How weight scales one quantity: the mass ratio to a power, kept within 0.7..1.4 so a kick scooter or a lorry still drives. The Volga is the reference (1). The exponents say how strongly: the same brakes stop a heavier car later (`BRAKE_WEIGHT`), its tyres hold a little less of it sideways (`GRIP_WEIGHT`) and its yaw follows the wheel later (`YAW_WEIGHT`, inertia). Air drag needs no weight of its own: the same drag force slows more mass less, so a heavy car coasts further by itself. An explicit `acceleration` is the car's own and is not scaled.
- `public static double friction(Tune tune)` — Tyre grip as an acceleration: (0.55 + `grip`) g, 0.83 g for the Volga and 0.87 g for the G 63.
- `public static double cornering(Tune tune)` — Sideways grip as an acceleration: `friction` × `CORNER_GRIP`, 2.8 g for the Volga and 3 g for the G 63 before weight (`GRIP_WEIGHT`) and `CORNER_MARGIN`.
- `public static double traction(Tune tune)` — The most the driven wheels put down: the whole grip with all-wheel drive, `REAR_SHARE` of it otherwise.
- `public static double torque(double revs)` — Engine torque over the share of `limit_rpm`, relative to the torque at full power (power = torque × revs, 1 at the limit): a turbocharged curve that spools up to 1.25 by 0.4, stays flat to 0.65 and falls to 1 at the limit, then the limiter cuts it over the next 20 %. The G 63's 850 Nm against 684 Nm at peak power is the same 1.25.
- `public static double engineForce(Tune tune, int gear, double speed)` — Pull at full throttle in a gear: specific power × torque / the gear's speed at the limit (power / speed, with speed = revs × that speed). Lower gears pull harder; below `LAUNCH` the engine is held at launch revs.
- `public static double drag(Tune tune)` — Air drag factor k (blocks/tick² per (blocks/tick)²): full power balances k·v² + `ROLLING` at `HEADROOM` × top speed. It comes out as a CdA of about 1.6 m² for the G 63 (the real car's is about 1.8).
- `public static double reachableSpeed(Tune tune, int gear, double power, int punctures)` — The speed a gear actually tops out at: its limit cut by a damaged engine and by flat tyres. `step` drives towards it and the automatic shifts against it, so a weak engine still changes up instead of waiting at a speed it can never reach.
- `public static double engineRpm(Tune t, int gear, double speed, double throttle, boolean live)` — Engine speed the drivetrain asks for. In gear it is tied to the wheels: speed over the gear's speed limit × `limit_rpm` (an automatic adds its converter slip under throttle); pulling away the converter or clutch holds it at its slip speed; in neutral it idles, or revs freely with the throttle. Before, it was speed over the gear's *reachable* speed (which falls with engine damage and flat tyres) plus 600 rpm whenever the throttle was down, and in neutral the gear's zero speed limit sent any rolling car to the rev cap: the tachometer read numbers that had nothing to do with speed and gear.
- `public static double revsToward(double current, double target)` — One tick of the engine following the asked speed at its own pace; a start goes straight to idle and a stop straight to 0 (the sounds wind down by themselves).
- `public static int automaticGear(Tune tune, int gear, double speed, double throttle, double power, int punctures, double up, double down)` — Next forward gear of an automatic, one step at a time; neutral and reverse are the driver's choice and pass through. Up at `up` of the gear's reachable speed under throttle (at 0.99 when coasting, only to avoid over-revving), down below `down` of the lower gear's. A floored pedal raises the down point to `up - 0.12` (kickdown) but never past it, so after any shift the new gear is already inside its own band and the box cannot hunt.

### DrivePhysics.drift(Tune,double,double,Input,Drift)

- `double corner = Math.min(1, Math.abs(steerAngle) / .20);` — The handbrake starts a controlled slide; steering alone only adds a small amount of slip at speed. Keeping the target below one avoids the pendulum effect that used to make the car snap from side to side. Neither braking nor throttle loosens the rear (`CORNER_GRIP`).
- `double cap = cornering(t) * weight(t.mass(), GRIP_WEIGHT) * CORNER_MARGIN / Math.max(speed, .05) * (1 + loss * 1.5);` — Yaw rate the tyres allow: lateral acceleration = speed × yaw rate, at most the cornering grip. Past that the car runs wide (understeer) instead of turning on rails; a sliding rear (`loss`) lets it rotate further.
- `double desired = Math.max(-cap, Math.min(cap, -Math.tan(steerAngle) * forward / t.wheelbase() * (1 + loss * .42)));` — Kinematic (bicycle) yaw rate of the wheel angle, capped by grip. Positive input is driver's left (+local X); Minecraft yaw turns the other way.

### DrivePhysics

- `Input input, Drift drift, double power) {` — power: share of engine output a damaged engine still gives (1 = intact). It weakens the pull and, less, the top speed of every gear.

### DrivePhysics.step(Tune,double,double,double,int,boolean,double,int,Input,Drift,double)

- `if (live && gear != 0 && (speed > .02 || gear <= 1)) {` — A standing vehicle may launch only in first or reverse. Higher gears can still be selected while rolling, but never start the car from rest.
- `drive = Math.min(demand, traction(t) * (1 - .12 * punctures)) * power * tyreEfficiency * Math.signum(gear);` — The engine's pull limited by what the tyres put down; engine damage comes after that limit, so a weak engine is felt even where a healthy one would spin its wheels.
- `double brake = Math.min(t.brake() * weight(t.mass(), BRAKE_WEIGHT), grip * BRAKE_GRIP);` — `brake` is the car's braking at full pedal; it can never stop the car faster than its tyres hold.
- `double engineBraking = live && gear != 0 && throttle < .05` — Engine braking fades out below launch revs (the clutch or converter slips) and is nothing past the limiter.
- `if (Math.signum(forward) == Math.signum(gear)) {` — Ease torque toward the gear limit while compensating road resistance. Downshifting cuts torque gradually; it never clamps existing momentum.
- `forward = approachZero(forward, Math.abs(forward * turn) * Math.abs(Math.sin(steerAngle)) * SCRUB);` — Cornering drag of the steered wheels (`SCRUB`).
- `double c = Math.cos(turn);` — Rotate conserved world velocity into the new vehicle frame.
- `double hold = grip * weight(t.mass(), GRIP_WEIGHT) * (1 + (CORNER_GRIP - 1) * (1 - drift.loss())) * Math.max(.1, 1 - drift.loss() * .9);` — How much sideways speed the tyres take out per tick: the cornering grip while they grip, falling to about the real grip and below as the handbrake loosens the rear (`loss`), so a handbrake slide still lasts about as long as before `CORNER_GRIP`. Without the game grip here a turn at the new yaw rates built up sideways speed faster than the tyres removed it.
- `lateral = approachZero(lateral, Math.min(Math.abs(lateral) * .7, hold));` — Tyres take out sideways speed in proportion to the slip while they grip, but never faster than their grip (`hold`, per tick): a slide wider than that decays only at the friction limit, so the car keeps sliding until the tyres catch it.
- `if (demand * power > traction * 1.15) slip += Math.min(.25, (demand * power / traction - 1) * .15) * (t.allWheel() ? .3 : 1);` — Wheelspin when the engine wants more than the tyres give: tyre marks and squeal at a hard launch, much less with all four wheels driven.

### DrivePhysics.canShift(int,int,double,int)

- `if (to > from && to > 1 && Math.abs(speed) < .06) return false;` — At a standstill only an upshift is forbidden. Downshifting from the top gear (or any other forward gear) must remain possible so the car can be put back into first and launched again.

## slimcore/src/main/java/studio/merami/slimcore/physics/Suspension.java

- `class Suspension` — Inertial body motion only (squat, dive, lean), ticked at 20 Hz. Terrain following is geometric: the body always sits on the plane through the wheels, so it can neither sink into blocks nor leave the wheels hanging below it. No noise is applied to a stationary car.
- `public double sway = 1;` — How far the body leans and dips: the mass ratio to 0.35 times the root of the height over the Volga's 1.95, set by `carry` every tick. The heavy, tall G 63 leans about 1.4 times as far as the Volga.
- `public double spring = .24;` — How fast the body follows; a heavier one settles more slowly (mass ratio to −0.35).

### Suspension.step(double,double,boolean)

- `double r = grounded ? clamp(lateralAcceleration * 1.8 * sway, -.045 * sway, .045 * sway) : 0;` — Positive lateral acceleration is a left turn: the right side compresses.
- `pitchVelocity = (pitchVelocity + (p - pitch) * spring) * .48;` — Overdamped response: no rebound past the target.

## slimcore/src/main/java/studio/merami/slimcore/systems/SeatedInteractions.java

- `class SeatedInteractions` — Shared client/server policy for riders: they use only their own vehicle, with either hand. The Fabric events and mixins that enforce it live in the interface.

## slimcore/src/main/java/studio/merami/slimcore/systems/VehicleSpawner.java

- `class VehicleSpawner` — Placing a vehicle for a player (spawn item and command), the owner's key and the vehicle a command acts on.

### VehicleSpawner.spawn(IWrapperPlayer,JSONVehicle,Point3D)

- `vehicle.keepClearOf(player);` — The new car moves forward off the player before anything else is checked, so the player is not moved.
- `vehicle.settleOnGround(96);` — Grass, flowers, a block side or a slab below must not make the car fall into place. `/slimvehicle spawn` while flying uses the player's height, so search well below it.
- `if (!world.allows(player, IWrapperWorld.Access.PLACE, vehicle)) {` — Zone rule `vehicle_place`, asked for the final settled box and before the entity is added: a refused car leaves no entity, no key and no used item (`ItemVehicle` shrinks the stack only for a placed car). Applies to `/slimvehicle spawn` too; operators pass the zones anyway.

## slimcore/src/main/java/studio/merami/slimcore/systems/RadioLibrary.java

- `final class RadioLibrary` — The radio music in one folder, read the same way on a server (`RadioServer`) and on a client (`RadioStations`, the player's own music): loose files are the station `OWN` (""), every subfolder a station of its name with any depth below it, stations by name ignoring case with `OWN` first, tracks in file name order. Only Ogg Vorbis, the one format the game decodes. Pure JDK, so a dedicated server, which has no LWJGL, reads lengths the same way as a client.
- `public record Track(String title, String hash, long size, double seconds, Path file)` — `hash` is the SHA-1 of the bytes in hex: players' caches and the transfer know a track by its content, so a renamed file is not downloaded again and a changed one is. `file` is null in a track that came over the network (`PacketRadioLibrary`).
- `public static final long MAX_SIZE = 128L << 20;` — Larger files are left out with a warning: a track is read into memory whole to hash it, and chunk offsets are ints.
- `public synchronized boolean refresh()` — Lists the folder (path, size and date of every file) and returns true when the stations changed. Run in the background every few seconds; only new or changed files are read (`known`, by path, size and date).
- `boolean settling = seen != null && !keys.equals(seen);` — A listing that differs from the previous one is not published yet: a file being copied or uploaded (FTP, a host panel) grows between looks, and publishing it half-written would send players a broken track and then a second version. Only when the folder held still for one interval does it count. The very first look publishes at once.
- `signature = null;` — A file that could not be read (still locked by the copy) is skipped and the next refresh reads the folder again even if nothing else changed.
- `public static double length(byte[] data)` — Length from the Ogg pages without decoding: the sample rate from the Vorbis identification header on the first page, the sample count from the granule position of the last page of that stream. The same as `stb_vorbis_stream_length_in_samples` (checked to the sample on all 51 Ogg files of the mod and the test music). 0 for anything that is not Ogg Vorbis (Opus, MP3 renamed to .ogg, text). The first page must start the file, as stb_vorbis requires; later junk is skipped to the next `OggS`, and a cut-off file ends at its last whole page.

## slimcore/src/main/java/studio/merami/slimcore/items/SlimItems.java

- `class SlimItems` — Core items: the key, the fuel can, the repair kit and one spawn item per vehicle. The interface registers a `BuilderItem` for each (`ItemRegistry`) during initialisation, before registries freeze.
- `public static Map<JSONVehicle, ItemVehicle> createVehicleItems()` — One spawn item per vehicle definition with `item: true`, in pack order.
- `public static final ItemKey KEY = new ItemKey();` — The one core item besides the spawn items with behaviour of its own (`ItemKey`).

## slimcore/src/main/java/studio/merami/slimcore/items/AItemBase.java

- `class AItemBase` — An item as the core defines it: its id, stack size, name and what it does on a block. The game item is a `BuilderItem` that asks this object; items without behaviour of their own (fuel can, repair kit) are plain `AItemBase`s, and the vehicle checks for them by identity (`player.mainHand().item() != SlimItems.REPAIR_KIT`).
- `public Text displayName()` — Null keeps the game's own name from the language file.
- `public boolean usedInAir()` — Items that act on a right click in the air (the key); `BuilderItem#use` calls `useInAir` on the server.

## slimcore/src/main/java/studio/merami/slimcore/items/ItemKey.java

- `public class ItemKey extends AItemBase` — The car key. It carries its car's UUID under `VEHICLE` (the tag 0.2.3 wrote) and works like a remote: a right click in the air (`useInAir`) locks or unlocks the car from up to `REACH` blocks, and with the key anywhere in the inventory the lock key (`K`) does the same (`VehicleControls#lockFromOutside`).
- `public static final double REACH = 32;` — Range of the key, and of the lock action for anyone holding a key (`AEntityVehicleE_Powered#action`).
- `public static UUID vehicleOf(IWrapperItemStack stack)` — The car a key opens, or null for anything else (another item, a key without a car).
- `public static AEntityVehicleE_Powered find(IWrapperPlayer player, UUID vehicle)` — The key's car if it is loaded within reach of the player.

## slimcore/src/main/java/studio/merami/slimcore/items/ItemVehicle.java

- `public Text displayName()` — The vehicle's name with a fallback, so a pack without translations still shows a readable name.

## slimcore/src/main/java/studio/merami/slimcore/entities/components/AEntityA_Base.java

- `abstract class AEntityA_Base` — Start of the entity chain: the host it lives in (`IWrapperEntity host`), its synced data and the helpers every level uses (rules, sounds by id, messages). Tick, load, save and data updates come from the host (`BuilderVehicle`).
- `public void emitSound(String sound, float pitch)` — Plays at the entity for everyone near; a silent name (`JSONSounds#silent`) plays nothing.
- `protected final void finishConstruction()` — Called last in the constructor of the concrete class. Vanilla's `Entity` constructor builds the bounding box before the host has its core entity; `BuilderVehicle#makeBoundingBox` asks the vehicle's hull only once construction is finished (`isConstructed`).

## slimcore/src/main/java/studio/merami/slimcore/entities/components/AEntityB_Existing.java

- `abstract class AEntityB_Existing` — Position and the server-authoritative motion clients replay (`PoseReplay`), plus the chassis/world transforms.
- `SyncedValue<Integer> POSE_TICK` — Server tick of the newest pose (position and HEADING), stamped whenever the vehicle has moved; clients replay these poses at an even pace.
- `private double lerpX;` — Newest position the server sent (client).
- `private Point3D stampedPosition;` — Last pose stamped for the clients (server).
- `public Point3D directionToLocal(Point3D world)` — A world direction turned into chassis axes (yaw only); `directionToWorld` the other way.
- `public Point3D worldToLocal(Point3D world, float partial)` — A world point in the chassis frame of the car as drawn in a frame (position and yaw interpolated). Anything measured in a frame (the camera) must be compared with this, not with the tick pose: at 120 km/h the car moves 1.7 blocks a tick, so a camera from the last frame lies one to two ticks of travel behind the car's tick position.
- `protected void stampPose()` — A pose for the clients' replay whenever the car has moved; a car at rest sends none.
- `public void lerpTo(double x, double y, double z)` — Only keeps the newest position (`BuilderVehicle#lerpTo` drops vanilla's rotation and step count): with its server tick and the precise heading from the data packet that follows it becomes a pose of the replay (vanilla rotation is quantized to 1.4 degrees).
- `public double lerpTargetX()` — Rotation-only network packets must retain the pending position, otherwise every steering update pulls the vehicle back towards its previous location.

### AEntityB_Existing.replayServerPose()

- `if (teleport) host.setOldPosAndRot();` — A teleport is shown in place, never glided across.

### AEntityB_Existing.onMovedTo(double,double,double)

- `replay.clear();` — Teleport packets place the car directly (`BuilderVehicle#moveTo`); drop any glide still aimed at the old spot.

### AEntityB_Existing.onSyncedDataUpdated(List<SyncedValue<?>>)

- `if (key == POSE_TICK) {` — A pose is complete once the whole packet is applied: its position came in the move packet just before, the heading may be anywhere in this one.

## slimcore/src/main/java/studio/merami/slimcore/entities/components/AEntityC_Renderable.java

- `abstract class AEntityC_Renderable` — The level animations ask: which JSON animation conditions hold.
- `public boolean animationActive(String condition)` — Whether a JSON animation condition holds; later levels add their states (`EntityVehicleF_Physics#animationActive`).

## slimcore/src/main/java/studio/merami/slimcore/entities/components/AEntityD_Definable.java

- `abstract class AEntityD_Definable<J extends AJSONItem>` — The definition the entity is made from, chosen by a synced id, and the synced state flags of all levels (`LOCKED`, `ENGINE`, `BRAKING`...: one int, one bit each).
- `public J definition()` — Cached by the identity of the synced id string, which stays the same instance until it changes.
- `public void setDefinition(String id)` — Always calls `onDefinitionChanged`, also for the same id: tests use it to rebuild the hull after changing part states.
- `public Animator animator()` — The entity's animation state, created on first use (on the client, by the renderer) and rebuilt when the definition or the loaded model changes (a resource reload clears the model cache). Null for a definition without a model.

## slimcore/src/main/java/studio/merami/slimcore/entities/components/AEntityE_Interactable.java

- `abstract class AEntityE_Interactable<J extends AJSONInteractable>` — The vehicle as something to collide with and to own: the hull and how it treats nearby entities, owner and keys, body condition by zone and dents.
- `SyncedValue<Float> HEALTH` — Body condition, 0..100 (see `DamageModel`); the engine's is on `AEntityVehicleE_Powered`.
- `List<SyncedValue<Integer>> ZONES = List.of(` — Damage of each zone of the body, 4 bits per zone (0..15), 8 zones per int.
- `SyncedValue<IWrapperNBT> DENTS` — Dents in the body (`DamageModel.Dent`), drawn by bending the model.
- `private static final double EMBED_MARGIN = .02;` — Overlap below EMBED_MARGIN (blocks) does not count as being in the car: touching it or standing on it. ESCAPE is the fastest push out of it (blocks/tick).
- `private final float[] zoneDamage = new float[AJSONInteractable.MAX_ZONES];` — Accumulated damage of each body zone, 0..100 (server); the client reads the synced nibbles.
- `private List<DamageModel.Dent> dentView = Collections.unmodifiableList(dents);` — Read-only view of dents: the same instance until the list is replaced, so the renderer can keep the bent mesh it built for it. A new view on every call rebuilt that mesh every frame.
- `public float zoneDamage(int zone)` — Damage of a body zone, 0..100 (in steps of 100/15 on the client).
- `public List<DamageModel.Dent> dents()` — Dents of the body, chassis units.
- `protected void wear(float amount, float engineShare)` — Lowers body condition by amount and the engine by its share of it (`wearEngine`).
- `protected void damage(Point3D local, Point3D push, APart part, float amount)` — Condition lost at a chassis-space point, struck in direction push (chassis axes, null: leaves no dent). The zone hit takes its share and the engine its part of it; a hit strong enough breaks what is in that zone (`breakInZone`: lamps, locks, glass) and the tyre that took it (`breakWheels`).
- `public void repair()` — Restores body and every zone and dent; later levels add engine, lamps, locks, glass and tyres.
- `public void setCondition(float percent)` — Sets the condition (percent), for testing damage effects.
- `public void refreshHull()` — Rebuild the hull and the broad-phase box, e.g. after the definition, glass or tyres changed.
- `public boolean hasKey(IWrapperPlayer player)` — A trusted player, or anyone with a key to this car in the inventory or the off hand (`IWrapperPlayer#inventoryItems`, `ItemKey#vehicleOf`). Owning the car is not a key: until 0.2.7 the owner and operators counted as holding it, so for the person who placed the car the key did nothing at all (they started and locked it with or without it). An owner who lost the key gets a spare with `/slimvehicle key`.
- `public BoundingBox makeBounds()` — The host's bounding box (`BuilderVehicle#makeBoundingBox`). Broad phase is only an index/search bound. All hits and collisions use `hull()`.
- `public void collisionBoxes(IWrapperEntity entity, BoundingBox query, List<BoundingBox> out)` — The vehicle as an obstacle for an entity moving near it (`EntityGetterMixin` turns the boxes into shapes): its boxes for people (`Hull#entityBoxes`, a smooth body side) as slices, and nothing at all for an entity caught inside the vehicle. A car that drove or turned into a player left them inside its hull: every move then touched another slice, the client refused it, and the server, which rejects any move into something new, bounced the player back, so they stood trapped. A box that only reaches into an entity stays solid: vanilla already lets it move out of a slice it overlaps, and `pushOut` takes it out. Letting go of every box an entity overlapped, as the first fix did, let a player walk through a door that closed on them into the cabin, where the low roof made them lie down.
- `public boolean embeds(IWrapperEntity entity)` — Whether an entity that is not riding the vehicle is caught inside it: its position over the body and its box inside one of the vehicle's boxes.
- `public boolean pushOut(IWrapperEntity entity)` — Pushes an entity out of the vehicle. One caught inside goes out through the nearest side whose way out is free of blocks, any side if none is. One that a box only reaches into (a door or lid that swung into it, a corner that turned into it) takes the shortest way out of the deepest such box (`Hull.Box#escape`): sideways at up to ESCAPE, or straight up onto it when that is shorter. Runs where the entity's movement is decided: the server for mobs, the client for its own player.
- `private double carried(Point3D out)` — How fast the vehicle itself moved this tick in the escape direction. Every push out adds it on top of ESCAPE: a car faster than ESCAPE (about 18 km/h) that drove into a standing player otherwise gained on them each tick, swallowed them and pushed them out of its side or back after some fourteen ticks inside the body. Now the player is pushed ahead of the bumper. The tick's travel is the position against the previous one (`position(0)`), which both sides keep for a moving car.

### AEntityE_Interactable.load(IWrapperNBT)

- `data.set(HEALTH, tag.has("Health") ? SlimMath.clamp(tag.getFloat("Health"), 0, DamageModel.FULL) : DamageModel.FULL);` — Vehicles saved without condition come back intact.

## slimcore/src/main/java/studio/merami/slimcore/entities/components/AEntityF_Multipart.java

- `abstract class AEntityF_Multipart<J extends AJSONMultipart>` — The vehicle as a set of parts: seats and their riders, openings with their storage, glass and wheels. Part objects are built from the definition and rebuilt when it changes (`ensureParts`); their synced state is a bit per part index in one int per kind.
- `SyncedValue<Integer> JAMMED` — Openings bent shut by a crash, by opening index.
- `public abstract float speed();` — Forward speed (blocks/tick); boarding, exits and openings refuse at speed.
- `public abstract Point3D chassisToWorld(Point3D local);` — Where a chassis point is with the current body pose (`AEntityVehicleD_Moving`); riders sit there.
- `public abstract Point3D restingBodyToWorld(Point3D local);` — Chassis point with only the static flat-tyre lean, as used by the collision contour; click zones follow it.
- `protected boolean anyShutOnMoveOpen()` — Moving off shuts every opening with `shut_on_move` (all but the glovebox by default), which can be opened and used while driving.
- `public void addRider(IWrapperEntity entity, Runnable attach)` — Hooks for `BuilderVehicle`'s passenger overrides. attach (detach in `removeRider`) is vanilla's own `addPassenger`, run at the point where the override used to call `super`, so the seat bookkeeping happens in the same order as before the split.
- `public boolean seated(IWrapperEntity rider)` — Whether a rider sits (rather than stands) in their seat; one not yet given a seat counts as the first.
- `public Point3D dismountLocation(IWrapperEntity passenger)` — Where `BuilderVehicle#getDismountLocationForPassenger` puts a rider: a floor spot beside the rider's seat, outside the hull and free of blocks.
- `public void requestExit(IWrapperPlayer player)` — Exiting through a door opens it and waits for the animation (`tickExits`); without a door, or through a jammed one, the rider climbs out at once.
- `public List<InteractionZone> interactionZones()` — Opening lids, tyres, seats behind an open door and the contents of open storage.
- `public Optional<InteractionZone.Hit> seatedTarget(int seat, Point3D eye, Point3D look, Point3D end)` — From a seat only the rider's own door and storages that list the seat (the glovebox, from the front seats) can be used: other doors, the bonnet, wheels and the rest are outside parts, and a ray from the cabin would otherwise land on them first. The own door is offered as close/open door. A storage is selected by aiming at it (STORAGE_AIM); a closed lid opens, an open one shows its contents.
- `private static final double STORAGE_AIM = .08;` — From a seat a storage (the glovebox lid or its contents) is selected only by looking at it: its box grown by this much (blocks), which covers the small offset between the eye used for the ray and the first-person camera. Turning towards it at any pitch, as before, offered it across half the field of view.
- `private static final double DOOR_HEADING = Math.toRadians(25);` — A rider selects their own door by turning within DOOR_HEADING of it while looking at least DOOR_DOWN below the horizon, or by looking straight at it.
- `private static final int EXIT_TICKS = 14;` — Give the client time to receive and visibly animate the door before detaching.
- `public IWrapperInventory storage(PartOpening part)` — Inventory behind an opening, sized by its definition; items that no longer fit after a resize drop at the vehicle.
- `protected boolean useHeldItem(IWrapperPlayer player)` — Hook for items used on the vehicle as a whole (the repair kit, `AEntityVehicleE_Powered`).
- `protected boolean useItemOnPart(IWrapperPlayer player, PartOpening part)` — Hook for items used on an opening (the fuel can on the fuel flap).

### AEntityF_Multipart.alignRider(IWrapperEntity)

- `float delta = SlimMath.wrapDegrees(yaw() - prevYaw());` — Called from `BuilderVehicle#positionRider` after the rider is moved to `riderPosition`. Only the current yaw turns with the car. The previous yaw stays put so the camera interpolates the turn every frame, exactly like the car body; shifting both made the view step at 20 Hz against a smoothly turning cabin.

### AEntityF_Multipart.dismountLocation(IWrapperEntity)

- `return new Point3D(x(), bounds().maxY + .05, z());` — If both sides are blocked, use a clear position above the roof.

### AEntityF_Multipart.board(IWrapperPlayer,int)

- `if (!world().allows(player, IWrapperWorld.Access.ENTER, this)) {` — Zone rule `vehicle_enter`, before the lock, seat and door checks, for every way of boarding (a click, a seat zone, a door, `boardOnClick`). Forced riding (`startRiding(…, true)`, commands) is no player boarding and is not asked.
- `if (flag(LOCKED) && !hasKey(player)) {` — The key matters at the door only when the car is locked. Anyone may sit behind the wheel of an unlocked car; starting the engine is what needs the key (`AEntityVehicleE_Powered#action`). The driver's seat used to refuse a player without the key, which with the owner counting as a key holder never showed.

### AEntityF_Multipart.interactPart(IWrapperPlayer,boolean)

- `if (flag(LOCKED) && !hasKey(player) && !(inside && zone.part() instanceof PartOpening door && door.isDoor())) {` — A rider opens and closes their own door from inside without a key, like a real lock. From a seat the only door offered is the rider's own (`seatedTarget`).
- `if (part.isDoor() && !inside && (enter || player.isSneaking())) {` — A rider clicking their door toggles it; boarding through it is for someone outside.
- `if (seat.door() == part && (chosen == null || …` — Several seats may share a door (the G63's rear bench: outer and middle seat behind the rear left door). Boarding through it takes the first free one, or the first one to report it taken.
- `if (Math.abs(speed()) > .035 && !part.definition.usableWhileMoving) {` — Only openings marked usable while moving (the glovebox) open on the move.
- `if (world().allows(player, IWrapperWorld.Access.STORAGE, this)) openInventory(player, part);` — Zone rule `containers`, as for chests. Only the contents are refused; the lid still opens and closes.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/APart.java

- `abstract class APart` — A part of a multipart entity: its owner, its index (the bit in the owner's synced flags), a label and the colour of its click zone. Parts are plain objects, not entities; their synced state lives on the owner.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/PartSeat.java

- `class PartSeat` — A seat and its window, with the window's animation progress (0..1).

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/PartOpening.java

- `class PartOpening` — A door, lid or flap: its open/jammed state, its opening animation (`progress` 0..1, eased toward the synced state every tick) and its inventory.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/PartGlass.java

- `class PartGlass` — A pane; its index is the bit in the broken-glass flags.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/PartGroundDevice.java

- `class PartGroundDevice` — A wheel: its definition, puncture state and visual suspension travel (chassis units, + up) of this and the previous tick.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/VehicleAction.java

- `enum VehicleAction` — Driver and rider commands sent by the client (`PacketVehicleAction`, as the ordinal).
- `RADIO_NEXT,` — Added after `ENTER`: new actions go at the end, so the ordinals of the old ones stay what older notes and tests know.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/AEntityVehicleC_Colliding.java

- `abstract class AEntityVehicleC_Colliding` — The vehicle against the world: building the hull from the definition and the model, sliding, climbing, turning against walls and other vehicles, collision impulses, crash damage, knocked-down blocks, people in the way, lamps and the spawn placement helpers. `forward`, `lateral` and `drift` are the motion state the drivetrain (`AEntityVehicleE_Powered#driveStep`) and the collisions share.
- `SyncedValue<Integer> LAMPS` — Broken lamps (`HEADLIGHT_*`, `TAILLIGHT_*`).
- `private static final double TURN_PUSH = .3;` — How far a turn may push the car sideways off a wall its corner swings into.
- `private static final double SMASH_SPEED = .25;` — Speed (blocks/tick, about 18 km/h) from which tagged blocks are knocked down instead of stopping the car; a heavier car breaks through earlier (`SMASH_WEIGHT`) and loses less speed doing it.
- `private static final double HIT_WEIGHT = .5;` — Weight in a hit on someone: the damage grows with the root of the mass ratio (`HIT_WEIGHT`), the throw a little less (`THROW_WEIGHT`), and the car keeps more of its speed. `LANDING_WEIGHT`: a heavier body lands harder.
- `private static final double SHOVE = .08;` — Someone in the way at walking pace moves away at no less than the car's closing speed plus this (blocks/tick), so the car pushes them ahead instead of driving over them into its hull. A player moves only if the server marks the new velocity for sending (`IWrapperEntity#markHurt`); a bare push never reached the player's client.
- `private static final double SPAWN_STEP = .25;` — `keepClearOf` moves a new car forward in steps of SPAWN_STEP, up to SPAWN_REACH blocks, until it stands SPAWN_GAP clear of the player.
- `public ChassisGeometry.Pose puncturePose()` — Static lean caused by flat tyres. Physics, hit-boxes and the model all use it.
- `public Hull buildHull(float yaw)` — Body boxes, solid openings, panes and tyres; suspension never moves the physics hull.
- `private APart partOf(String name, Map<String, ModelSkeleton.Bone> bones)` — Part a model bone belongs to: its own opening, else its parent's, else the body.
- `public Optional<Hull.Hit> cabinHit(Point3D start, Point3D end)` — The first thing a shot fired from inside the car meets on its way out: an intact closed pane, the body, a door, the bonnet or a lid. Lowered and broken panes are not in the hull, so they let the shot out. A door's box is the outline of the whole door with its window (`panel`), so a hit on a door counts only where the shot does not pass through that door's window opening (`windowOpenings`); otherwise an open or broken window would still stop it. Seats and trim are not physical, and other riders are not targets, as in TaCZ.
- `private List<Hull.Box> windowOpenings()` — Every pane at its closed position, whatever its state, grown by `WINDOW_FRAME`, with the opening it sits in as its part: the window holes of the doors.
- `private static final double WINDOW_FRAME = .12;` — A shot this close to a window's edge still counts as through the window, not through the door frame around it.
- `public void cabinShot(Point3D start, Point3D end, float damage)` — A bullet from inside (`EntityKineticBulletMixin`): the pane it meets breaks; the body or a door takes the same share as from outside (`projectileHit`). Riders are never hurt by it, unlike `projectileHit`, which tests every passenger along the line and would hit the shooter at the start of it.
- `private Matrix4d body(Matrix4d root)` — The body above the chassis with the lean of flat tyres, the same for the hull and the window openings.
- `public double inertia()` — Yaw moment of inertia of a uniform box of the vehicle's size, in mass·blocks² (mass relative to `REFERENCE_MASS`).
- `public Point3D velocity()` — Velocity in blocks/tick on world axes.
- `public void push(Point3D change, double spin)` — A collision impulse from outside: a velocity change (blocks/tick, world axes) and a yaw-rate change (radians/tick, as Minecraft yaw).
- `protected HullCollision.World surroundings(float yaw)` — Block boxes and vehicles around the hull, once per tick: enough for the turn, its push-off and the move.
- `protected boolean turn(float yaw, HullCollision.World world)` — Rotate in place. A corner swinging over a slightly higher block (a path edge, slab or snow layer) lifts the car onto it exactly like a forward step; cancelling the whole turn for that tick, as before, made the car twitch in corners on uneven ground. A corner swinging into a wall or another car pushes the car off it, so a car pressed against a wall can still steer away. Only when that is not enough is the turn refused.
- `protected Point3D slide(Point3D velocity, HullCollision.World world)` — Moves by one tick of velocity (blocks/tick, world axes). What it meets is knocked down (a tagged block, at speed), climbed (up to max_step) or hit: the car stops at the surface, keeps the motion along it, and bounces or turns off it. Returns the velocity left, the same instance if nothing was hit.
- `private Point3D collide(Point3D velocity, HullCollision.Contact hit, Point3D center)` — Collision impulse at a contact, from the speed at which the car closes on the surface: that part is reversed with a little bounce, sliding along the surface is slowed by friction, and a hit off the centre line turns the car. Another vehicle takes the opposite impulse. Returns the new velocity.
- `private static double lever(Point3D r, Point3D n)` — Turning arm of a horizontal force along n applied at offset r from the centre: (r × n)·up.
- `private void impact(HullCollision.Contact hit, double deltaV, double slide)` — Sound, debris, sparks and damage of a contact; slide is the speed along the surface.
- `private double smash(Hull hull, Point3D from, Point3D to, HullCollision.World world, Point3D velocity)` — Knocks down the blocks of `BREAKABLE` the move runs into, removing them from world. Returns the share of speed kept: harder blocks take more of it.
- `protected void strike(Point3D velocity)` — People and animals in the way: shoved aside at walking pace, hit and thrown at speed.
- `public void crash(Point3D point, APart part, double deltaV, Point3D push)` — Damage from a collision that changed this vehicle's speed by deltaV (blocks/tick) at a world point; push is the world direction the body was struck in (into the car).
- `protected void land(double speed)` — A drop onto the wheels: body and engine take the landing, occupants a fall.
- `private Point3D[] blast(Point3D from)` — Where a blast from a chassis-space point meets the body, and the direction it pushes it in.
- `protected double sampleGround(double x, double z, double halfLength)` — Highest block top under a yaw-aligned footprint around a chassis point (x, z).
- `protected Point3D tryStep(Hull before, Hull hull, Point3D travel, Point3D candidate, HullCollision.World world)` — Lift that lets the move from before+travel to hull+candidate climb a low obstacle (kerb, slab, closed trapdoor; up to max_step), or null. before is the hull at travel: the same hull for a move, the unrotated one for a turn.
- `public void keepClearOf(IWrapperPlayer player)` — A car placed by its centre on the block the player clicks reaches 3.3 blocks back, often over the player, who was then moved aside with a jolt. The car moves forward, along the player's view, until it is clear of them; if it cannot (blocks, or more than SPAWN_REACH), it stays where it was and the player is moved as before.
- `public void settleOnGround(double maxDrop)` — Rest a freshly placed car on the surface below instead of letting it drop out of the air.
- `public Optional<Point3D> spawnClearance(IWrapperPlayer player)` — Place a spawning player completely outside the car, including its empty cabin.

### AEntityVehicleC_Colliding.buildHull(float)

- `ChassisGeometry.Pose lean = puncturePose();` — Flat tyres lean the whole body. Apply exactly the model's chassis transform (model space mirrors X and Z) so hit-boxes stay on the panels.
- `boolean flat = wheel.isPunctured();` — A punctured tyre is squashed around its hub (model scale .7/.82) with the hub lowered, so the tread still touches the road and the contour matches the visible tyre.

### AEntityVehicleC_Colliding.panel(JSONVehicle,ModelSkeleton.Bone,PartOpening,APart)

- `if (max > 0 && Math.abs(box.center.x) > max) continue;` — A part with max_half_width leaves out the cubes that sit outside it (the door mirror): with the mirror the front doors were taller and longer than the rear ones and stuck out of the body side. One merged box per model, bone and trim is cached for all vehicles.

### AEntityVehicleC_Colliding.boneMatrix(ModelSkeleton.Bone,Map<String,ModelSkeleton.Bone>,Map<String,Matrix4d>,Matrix4d)

- `Point3D push = DamageModel.displacement(dents(), new Point3D(-bone.pivot().x, bone.pivot().y, -bone.pivot().z));` — The hinge of a part that opens moves with the dents, as in `VehicleRenderer#hinge`, so the part's box and click zone stay on the bent body and on the drawn part. The vehicle rebuilds its hull when its dents change.
- `PartGlass pane = glassByBone(bone.name());` — Tyres use their stationary round envelope; rolling them does not change collision.

### AEntityVehicleC_Colliding.slide(Point3D,HullCollision.World)

- `velocity = Point3D.ZERO;` — The surface could not be told apart (a wedge of tilted boxes): stop at the last free spot.

### AEntityVehicleC_Colliding.collide(Point3D,HullCollision.Contact,Point3D)

- `Point3D along = relative.add(n.scale(approach));` — Friction may stop the slide along the surface but never reverse it.
- `drift = new DrivePhysics.Drift(SlimMath.clamp(drift.yawRate() - arm * impulse / inertia(), -MAX_SPIN, MAX_SPIN),` — Minecraft yaw grows clockwise seen from above, against the right-handed spin.

### AEntityVehicleC_Colliding.impact(HullCollision.Contact,double,double)

- `world().spawnParticles(IWrapperWorld.Particle.SPARK, point, 2, .05, .05, .05, .15);` — Grinding along a wall or another vehicle.

### AEntityVehicleC_Colliding.projectileHit(Point3D,Point3D,IWrapperDamage,float)

- `IWrapperEntity victim = null;` — Upper cabin is glass: ray-test passengers instead of treating the complete body box as armour.
- `hull().ray(start, end).ifPresent(body -> damage(worldToLocal(body.point()), null, body.box().part, damage * .3f));` — The body takes a share; a round in a lamp breaks it. Bullets leave holes, not dents.

### AEntityVehicleC_Colliding.attack(IWrapperDamage,float)

- `if (!world().allows(attacker, IWrapperWorld.Access.DAMAGE, this)) return false;` — Zone rule `vehicle_damage` (see `MosvegasZones`) for shots, explosions and the owner's creative removal. The attacker is the player behind the damage, or null (TNT without an igniter, mobs), and null is never a zone member.
- `if (attacker != null && attacker.isCreative() && attacker.isSneaking() && isOwner(attacker)) {` — A player's own blow (fist or held item) does nothing to the car apart from the owner's creative removal: since 0.2.5 it no longer breaks the pane they look at. Glass breaks from bullets and other projectiles (`projectileHit`), explosions and crashes.

### AEntityVehicleC_Colliding.tryStep(Hull,Hull,Point3D,Point3D,HullCollision.World)

- `if (!onGround()) {` — At seams onGround may briefly be false, even with a tyre 1 cm above the road. Never turn that harmless gap into a wall or allow air climbing.
- `for (int pass = 0; pass < 4; pass++) {` — Lift by the underside of the part touching the obstacle. Using the lowest tyre for a bumper contact made the whole car jump half a block.
- `if (touch.block() == null || touch.block().maxY - y() > maxStep) return null;` — Another vehicle is never climbed.
- `for (double y = travel.y + .08; y < lift + .08; y += .08) {` — Both the upward sweep (of the hull as it stands) and the raised hull must clear the roof, walls and other vehicles. Never stair-step through an overhead block.

### AEntityVehicleC_Colliding.moveVertically(double)

- `Hull hull = hull();` — Rest on the visible tyre/body surfaces, not on a giant entity box.
- `BoundingBox slice = box.bounds;` — Yaw-only lower hull/tyres have a flat underside. SAT rejects empty corners without creating thousands of tiny vertical collision boxes.

### AEntityVehicleC_Colliding.collides(Point3D,float)

- `if (yaw != yaw()) adoptHull(hull, yaw);` — Keep the just-built rotated contour. Without this, every turn rebuilt the complete model once for the yaw test and once again for translation.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/AEntityVehicleD_Moving.java

- `abstract class AEntityVehicleD_Moving` — Steering and the driver's input, the motion of one tick (turn, slide, fall, land, people in the way) and the suspension: synced wheel heights and inertial lean on the server, the settled body and wheel pose on both sides.
- `private static final float WHEEL_DROOP = .04f;` — Maximum visible droop of a wheel below its arch (WHEEL_BUMP: rise into it), in chassis units.
- `public double steeringPull()` — Steering offset of bent front wheel arches, radians (+ pulls left).
- `public Point3D chassisToWorld(Point3D local, float partial)` — A chassis point with the body pose interpolated for a frame, for rendering.
- `public float leanPitch;` — The inertial part of the body pitch (squat and dive from `Suspension`), eased like the body itself, so `levelledDirection` can take it back out.
- `public Point3D levelledDirection(Point3D local, float partial)` — A chassis direction for a frame with self-levelling (`ChassisGeometry#levelled`): what the headlight beams use.
- `private static final int ZONE_RECHECK = 20;` — `driveAllowed` asks the zones again when the box reaches other blocks, when the driver changes, or after this many ticks; the last catches a zone closed or a right revoked under a standing car. `ZONE_NOTICE`: ticks between two "driving is not allowed" messages to the driver.
- `protected boolean driveAllowed()` — Zone rule `vehicle_use` for the current driver over the whole box (`IWrapperWorld#allows`), cached (`ZONE_RECHECK`). A car without a player at the wheel is always allowed. One answer costs the protection mod a few dozen block lookups, so it is asked only when it may have changed, not every tick.
- `private void stopAtZone(Point3D start, float yaw)` — Puts the car back where the tick started, heading included, with no speed and no spin. Unlike a real wall there is no crash, damage, sparks or scrape sound.

### AEntityVehicleD_Moving.tickControls()

- `controllingPassenger() == null ? 1 : brakePressure, input.steer(), input.handbrake());` — Without a driver the car stands on its parking brake: a shove from another car slides it a little, it does not roll away.
- `if (!driveAllowed()) {` — Where driving is forbidden the controls do nothing and the brake holds. The driver gets `message.slimvehicle.zone.use` only while pressing throttle or steering, not just for sitting there.

### AEntityVehicleD_Moving.tickSteering()

- `double pull = Math.abs(forward) > .02 ? steeringPull() : 0;` — A bent wheel arch pulls the car to one side once it rolls; the driver has to hold against it.

### AEntityVehicleD_Moving.tickMotion(double,double,double,double)

- `boolean turning = Math.abs(nextYaw - oldYaw) > 1e-5;` — Query the surroundings once for the turn, the move and its contacts. Neighbour contours are resolved once per tick too: rebuilding and SAT-testing the same 20+ boxes for every movement substep was the main source of server spikes while steering or accelerating. A car standing still (most parked cars) does not query anything.
- `lateral = 0;` — A wall or another vehicle stops the rotation: keep the velocity on the unchanged heading instead of letting the slide build up against it.
- `double vy = Math.max(-1.2, motion().y - .08);` — Suspension is visual; tyres keep a stable contact height while steering.
- `if (entity.isLiving() && entity.asPlayer() == null) pushOut(entity);` — Mobs in the car are pushed out on the server; a parked car looks every five ticks. Players are pushed by their own client (`VehicleControls`).
- `if (allowedBefore && !driveAllowed()) {` — A zone that forbids driving is a wall for a car coming from outside: a step whose box would reach into it is undone (`stopAtZone`) and the car stands at the border, free to reverse or turn away. Checked after the whole step, turn included, so a corner swinging in is stopped too. Without it the car would roll in, lose its controls inside and be stuck. A car already inside (the zone was made around it, a right was revoked) gets no wall and only loses its controls.

### AEntityVehicleD_Moving.tickWheels(float,double,double,double)

- `int count = wheels().size();` — Query the complete tyre footprint, including thin collision shapes. A deep hole can only extend a wheel through its actual suspension travel.
- `double[] heights = new double[count];` — Wheel-centre offsets from the resting height. The body follows the plane through them on both sides (updateSuspensionVisuals), so it can never sink under blocks nor leave a wheel hanging below the arch.

### AEntityVehicleD_Moving.updateSuspensionVisuals(double,double)

- `if (visualsReady && x() == restX && y() == restY && z() == restZ && yaw() == restYaw && previousY == y() && authoritativeY == y()) {` — A parked car keeps its settled pose: no ground probes once nothing has changed for a second. With dozens of cars around, re-sampling every parked one each tick added up.

### AEntityVehicleD_Moving.settleSuspension(double,double)

- `double[] offsets = new double[count];` — Wheels are kept in world space while the hull moves, then follow the road. A tyre meeting a step climbs it within two ticks (never more than a sliver inside the block); dropping off an edge is eased over a couple of ticks.
- `ChassisGeometry.Pose plane = ChassisGeometry.wheelPlane(definition, offsets);` — The body rests on the plane through its wheels plus the small inertial lean.
- `leanPitch = visualsReady ? SlimMath.lerp(.5f, leanPitch, data.get(PITCH)) : data.get(PITCH);` — Eased at the body's own rate, so the levelled beam does not twitch while the body is still catching up with a change of lean.
- `for (int end = 0; end < 2 && !definition.underbody.isEmpty(); end++) {` — The overhangs never dip into what is under them (a bumper resting on a slab before the wheels reach it). Like a real car the nose (or tail) rides up, pivoting about the opposite axle, instead of sinking into the block.
- `if (!visualsReady) {` — First tick after spawning or entering view: appear already settled.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/AEntityVehicleE_Powered.java

- `abstract class AEntityVehicleE_Powered` — Engine, fuel, gears and the equipment a driver switches, the repair kit and the fuel can, and the driver's commands.
- `private static final int ENGINE_EQUIPMENT = RADIO | WIPERS;` — Everything that needs a running engine; switched off together with it.
- `public float fire()` — How hard it burns, 0..1 (see `DamageModel#fire`).
- `public float fuelLeak()` — Fuel lost per tick through a crushed tank: from 30 % damage of a leaking zone, up to 0.004 l.
- `private static final int AUTOMATIC_SHIFT_TICKS = 8;` — Pause after any shift before the automatic changes again: a shift is felt (the revs settle) and two shifts can never land in one burst.
- `SyncedValue<Integer> START_TICK` — Server game time of the last engine start. A client that receives it within `FRESH_START` ticks takes it as a start it witnesses (`startedAt`, client ticks) and plays the start sound, the catch smoke and the loops' fade-in from it; a client that first sees an engine already running gets an old stamp and plays none of it.
- `SyncedValue<Integer> POP_SEQUENCE` — Counts exhaust pops. The server decides a pop, plays its sound at a tailpipe and bumps this; clients flash the pipes on the change (`popAt`). Before, the sound came from the server's guess and the flames from the client's own, so they rarely matched.
- `private static final double OVERRUN_REVS = .65;` — Overrun pops need revs above this share of `limit_rpm` when the throttle is lifted (the old fixed 3800 rpm was this on the Volga). `OVERRUN_PAUSE` and `SHIFT_POP_PAUSE`: least ticks since the last pop.
- `public Point3D enginePosition()` — Where engine sounds come from: the engine bay, else the centre. Outside the cabin box, so `CabinAcoustics` muffles them for the people inside.
- `protected void tickTransmission(DrivePhysics.Input applied)` — The automatic's gear choice, only with a driver and a running engine. Throttle from neutral at a standstill engages first (D) with the selector sound; the rest goes through `DrivePhysics#automaticGear` silently and without the lever-reach animation (`SHIFT_SEQUENCE`).
- `protected double driveStep(DrivePhysics.Input applied, float steer)` — One tick of the drivetrain on the shared motion state; returns the yaw change. The synced revs follow the asked engine speed at the engine's pace (`DrivePhysics#revsToward`).
- `case GEAR_UP -> shift(definition.transmission.automatic && gear() >= 1 ? gear() : gear() + 1, player);` — With an automatic the gear keys move the selector R–N–D: up stops at D, down from any forward gear goes to neutral, then reverse.
- `public void shift(int target, IWrapperPlayer player)` — Gears beyond the definition's are ignored without a message, and so is the gear already engaged. A shift towards reverse plays `gear_shift_down`.
- `SyncedValue<Integer> LOCK_FLASH` — Server game time of the last lock or unlock; clients flash the indicators from it (`lockFlash`).
- `SyncedValue<Integer> RADIO_STATION` — The station the radio is tuned to, a counter the driver turns with `RADIO_NEXT` and `RADIO_PREVIOUS`. The server only counts and every client takes the count modulo its station list (`RadioStations`): on a server with music in its radio folder that list is the server's (`RadioServer`), the same for everyone; elsewhere it is the client's own music, and players with the same music hear the same station. Saved as `RadioStation` only when it is not 0, so a car's NBT stays as older versions wrote it.
- `case RADIO_NEXT, RADIO_PREVIOUS -> {` — Like the radio key, it needs a running engine. With the radio off it only switches the radio on, on the station it was left at.
- `public boolean toggleLock(IWrapperPlayer player)` — Locks or unlocks for a player with a key (`hasKey`): the lock sound, a message and the indicator flash. Without a key it only says so (`message.slimvehicle.no_key`).
- `public boolean lockFlash(long gameTime)` — Whether the lock flash lights the indicators at this game time: once for 10 ticks on locking, twice for 5 on unlocking, like a key fob. `VehicleLights` and `VehicleRenderer` light the indicator lamps from it on top of the turn signals.
- `double reach = action == VehicleAction.LOCK ? ItemKey.REACH : 8;` — Only the lock works from afar, like a key fob; every other action needs the player within 8 blocks.
- `private int parkedUntil = -1;` — Tick at which an engine left running without a driver switches off; -1 while someone drives or the engine is off.
- `protected void onRiderLeft(int seat)` — The driver gets out. A running engine keeps idling for `engine.keep_running` seconds: lights, radio and wipers stay as they were, the siren, brake and turn signals go off and the gearbox goes to neutral (a car without a driver stands on its brakes). With `keep_running` 0 or the engine off everything is switched off at once (`switchOff`), as before.
- `protected void tickParkedEngine()` — Counts `engine.keep_running` down while nobody drives a running car, then switches it off with the stop sound. A driver who gets in stops the count; the next exit starts it again.
- `public int parkedEngineTicksLeft()` — Ticks until a parked running engine stops, -1 when none is counting.
- `public static final int HORN = 512;` — The horn is a held state, not a one-shot action: the flag is on while the driver holds the key and every client plays the horn loop for as long as it is on (`VehicleAudio.Kind.HORN`). A one-shot sound of fixed length could not follow a short tap or a long blast.
- `private static final int HORN_RELEASE = 6;` — Ticks without a held-horn input before the horn stops by itself (`tickHorn`), for a driver whose input packets stop arriving (lag, a closed connection, a screen opened mid-blast).
- `if (!driver(player)) return;` — Every command in `action` is the driver's. Starting or stopping the engine also needs the key (`hasKey`), otherwise `message.slimvehicle.need_key`; the lock (handled before) needs it too.
- `public void hornInput(IWrapperPlayer player, boolean held)` — Fed by every input packet (`NetworkHandler`); a passenger's is ignored. A held key refreshes `hornHeld`, a released one clears the flag at once.
- `protected void tickHorn()` — Clears the horn once the driver is gone or their input went quiet for `HORN_RELEASE` ticks. The flag is also cleared on load, when the driver leaves a running car (`onRiderLeft`) and by `switchOff`.
- `else if (!flag(ENGINE) && !driveAllowed()) message(player, "message.slimvehicle.zone.use");` — The engine does not start where driving is forbidden (`driveAllowed`); switching it off is always possible.
- `private void toggleEngine(IWrapperPlayer player)` — Starting and stopping send no message any more: the tachometer readout (lit digits, needle) and the sounds already say it, and the action-bar text was one more thing to read. The same went for the wipers switch. Refusals (wrecked, dead engine, empty tank) still explain themselves.

### AEntityVehicleE_Powered.tickWipers()

- `boolean running = flag(WIPERS) && flag(ENGINE);` — Wipers run only from their own switch with the engine on. Switched off on the way up, the blades turn back from where they are (the sweep is symmetric) and park.

### AEntityVehicleE_Powered.tickEngine(DrivePhysics.Input)

- `if (definition.exhaustEffects.overrun && flag(ENGINE) && rpm() > definition.engine.limitRpm * OVERRUN_REVS && previousThrottle > .7 && applied.throttle() < .1` — Lifting off at high revs pops the exhaust once (`pop`).
- `if (flag(ENGINE) && (wrecked() || engineHealth() <= 0 || engineHealth() < 20 && random.nextInt(400) == 0)) stall();` — A dead engine stops; a badly damaged one now and then stalls on its own.

### AEntityVehicleE_Powered.load(IWrapperNBT)

- `setFlags(flags() & ~(ENGINE | BRAKING | SIREN | HORN | ENGINE_EQUIPMENT));` — A loaded vehicle stands with the engine off and the horn silent, whatever was running when it was saved.

## slimcore/src/main/java/studio/merami/slimcore/entities/instances/EntityVehicleF_Physics.java

- `class EntityVehicleF_Physics` — The concrete vehicle every definition uses. Its tick runs the levels in a fixed order: part animation and visuals on both sides; on the server riders and exits, input, the horn's release timer (`tickHorn`), engine, the timer of an engine left running without a driver, the automatic gearbox, steering, shutting openings, the drivetrain, then motion, suspension and the pose stamp.
- `public boolean animationActive(String condition)` — Conditions for JSON animations: a vehicle state, `open:<opening>`, `gear:<n>` or `seat:<n>` (occupied); a leading `!` negates any of them, so a pair of play-and-hold animations can move a part one way and back (the G63's mirrors fold on `locked` and unfold on `!locked`).

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/SlimVehicle.java

- `class SlimVehicle` — Fabric entry point. Order matters: the core interface first, then the packs (items are made from them), then registration of sounds, particles, rules, the entity and items, networking, events and commands.
- `public static ResourceLocation id(String path)` — The interface's resource locations in the mod's namespace; the core uses `SlimCore#id` for its own `ResourceId`s.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/InterfaceCore.java

- `final class InterfaceCore` — `IInterfaceCore` on Fabric 1.20.4: NBT is a `CompoundTag`, an inventory a `SimpleContainer`, an item stack an `ItemStack` of the item registered for the core item.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/entity/BuilderVehicle.java

- `class BuilderVehicle` — The game entity of every vehicle (`slimvehicle:vehicle`, the id of 0.2.3), Immersive's `BuilderEntityExisting`. Vanilla keeps doing what it did for the old entity class (position, rotation, passengers, tracking, chunk saving); every override hands the decision to the `EntityVehicleF_Physics` it holds. Each override sits where the old entity overrode the same vanilla method, so the call order and the network traffic are unchanged.
- `private static final List<SyncedValue<?>> VALUES = SyncedValue.of(EntityVehicleF_Physics.class);` — One `SynchedEntityData` accessor per core value, defined in the core's order (`SyncedValue#index`), so the ids are those of 0.2.3. `BY_INDEX` maps a core value to its accessor, `BY_ID` an incoming accessor id back to the core value.
- `Object value = entityData.get(BY_INDEX[key.index]);` — NBT values travel as `CompoundTag` and are wrapped on the way in and out; the other kinds are boxed Java values on both sides.
- `vehicle = new EntityVehicleF_Physics(WrapperEntity.of(this), data);` — Vanilla's constructor runs first: it defines the data (`defineSynchedData`) and builds a bounding box before the vehicle exists, which is why the overrides check `vehicle == null`.
- `public static EntityVehicleF_Physics core(Entity entity)` — The core vehicle of an entity, or null for anything else. `ridden` is the vehicle an entity sits in; the client mixins and HUD use them instead of `instanceof`.
- `public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> values)` — The whole packet as one list of core values, so `AEntityB_Existing` can treat a pose stamp as complete only after the packet is applied.
- `public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps)` — Vanilla's interpolation is replaced by the vehicle's pose replay; only the position is passed on.
- `public boolean isControlledByLocalInstance()` — Server-side physics must remain authoritative even with a local driver.
- `protected void positionRider(Entity passenger, MoveFunction move)` — The core says where the rider sits (`riderPosition`) and turns them with the car (`alignRider`); vanilla's move function places them, so the client's interpolation of riders stays vanilla's.
- `public void refreshBounds()` — For the core's `IWrapperEntity#refreshBounds` after the hull changed: vanilla's `makeBoundingBox` is protected.
- `private static final ThreadLocal<Boolean> IGNORE_VEHICLES` — Set while vanilla decides whether a player fits a pose (`PlayerMixin`); `EntityGetterMixin` then leaves vehicles out of the entity collisions. Per thread, because the client and the integrated server run pose checks at the same time.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/item/BuilderItem.java

- `class BuilderItem` — The game item of a core item (`AItemBase`): stack size, name and use on a block come from the core item.
- `BlockPos pos = context.getLevel().getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(context.getClickedFace());` — A vehicle goes into replaceable blocks (grass, snow) where it was clicked, otherwise onto the face clicked, as in 0.2.3.
- `public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)` — Right click in the air for core items that want it (`AItemBase#usedInAir`, the key): the server acts, and a short cooldown keeps a held button from locking and unlocking the car every few ticks.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/registry/EntityRegistry.java

- `.dimensions(EntityDimensions.fixed(2.0f, 1.82f)).trackRangeBlocks(160).trackedUpdateRate(2).build());` — The type's size is only the default box before the hull is built; tracked from far (160 blocks) and updated every other tick, unchanged from 0.2.3.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/wrapper/IWrapperHolder.java

- `interface IWrapperHolder` — A field on every `Entity` (`mixin/common/EntityMixin`) and `Level` (`LevelMixin`) that keeps its wrapper. A wrapper is made once per object, so the core can compare wrappers by identity, keep them in maps and hand them back, as it did with the vanilla objects themselves.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/wrapper/WrapperEntity.java

- `public static WrapperEntity of(Entity entity)` — Players get a `WrapperPlayer`, everything else a `WrapperEntity`; the same wrapper every time (`IWrapperHolder`).
- `if (position != lastPosition) {` — The converted position (and `bounds`) is kept while vanilla's object stays the same: vanilla replaces `Vec3` and `AABB` on every change, so identity tells whether anything moved, and the physics loop does not allocate a new point per call.
- `private static AEntityA_Base core(Entity entity)` — The core entity behind a vanilla one, used for `vehicle()` and `rootVehicle()`.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/wrapper/WrapperWorld.java

- `TagKey<Block> BREAKABLE` — Blocks a car knocks down at speed instead of stopping at them (data pack tag `slimvehicle:car_breakable`).
- `ResourceKey<DamageType> RUN_OVER` — "Run over by a car"; attributed to the driver, so it follows the server's PvP setting. Falls back to vanilla's fly-into-wall damage if a data pack removed the type.
- `if (!(level instanceof ServerLevel server)) return;` — Particles the core spawns are server particles sent to the clients near, as before the split; on the client they do nothing.
- `public List<AEntityA_Base> coreEntities(BoundingBox box)` — Other vehicles in a box, as core objects.
- `public boolean allows(IWrapperPlayer player, Access access, AEntityA_Base vehicle)` — Hands the game entities to `MosvegasCompat`.
- `return state.is(BREAKABLE) && hardness >= 0 && MosvegasCompat.terrain(level, pos) ? hardness : -1;` — A block a zone protects from terrain changes (Mosvegas `terrain`; in a protected world that is every block outside zones allowing it) counts as unbreakable, so the car stops at it as at any wall. The zone is asked only for tagged blocks, which are few.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/wrapper/WrapperPlayer.java

- `public void openStorage(IWrapperInventory inventory, int rows, Text title, Predicate<IWrapperPlayer> stillValid)` — A vanilla chest menu over the storage's own container, so the items are the vehicle's and never copied.
- `public List<IWrapperItemStack> inventoryItems()` — The main inventory and the off hand, so a key held in the off hand counts (`hasKey`). Armour slots are left out.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/wrapper/Convert.java

- `class Convert` — Conversions at the boundary: `Point3D`↔`Vec3`, `BoundingBox`↔`AABB`, `ResourceId`↔`ResourceLocation`, `Text`→`Component`.
- `public static Component text(Text text)` — A fallback becomes `Component.translatableWithFallback`; arguments that are `Text` themselves are converted too.
- `private static final Map<ResourceId, ResourceLocation> LOCATIONS = new ConcurrentHashMap<>();` — Resource locations are made once per id: the renderer asks for the texture every frame.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/common/LevelMixin.java

- `public class LevelMixin implements IWrapperHolder` — The level's `WrapperWorld`, kept on the level itself so it lives and dies with it.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/packs/PackDiscovery.java

- `class PackDiscovery` — Pack sources: this mod, every other mod with `slimpack.json` at its root, and the folders and zip files in `<game dir>/slimvehicle/packs/`. Zip file systems stay open for the game's lifetime because models are read from them on demand.
- `Path CONFIGS` — The per-vehicle configs the mod writes and reads (`VehicleConfig`): `config/slimvehicle/vehicles/<namespace>/<name>.json`.
- `Path OVERRIDES` — The older server-side changes to vehicles: `config/slimvehicle/overrides/<namespace>/<name>.json`, merged over the pack's definition after the config. Still read where someone made them, no longer created.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/registry/ItemRegistry.java

- `if (BuiltInRegistries.ITEM.containsKey(id)) {` — A vehicle whose name collides with an item already registered (another pack, or `key` in this namespace) keeps working but gets no spawn item.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/registry/SoundRegistry.java

- `class SoundRegistry` — The built-in sound events, registered so they travel by registry id and show up in `/playsound`.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/registry/ParticleRegistry.java

- `SimpleParticleType EXHAUST` — Sprites are the mod's own (`textures/particle/`, drawn by `tools/GenerateParticleTextures.java`) in the frame counts and order of the SnAssets Particles sheets they replace, so `VehicleParticles` picks frames as before.
- `SimpleParticleType FLAME` — Only to give the flame sheet (`particles/flame.json`) a particle id, so its sprites load into the atlas; nothing spawns it as a vanilla particle.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/network/NetworkHandler.java

- `class NetworkHandler` — Server side of the two client packets: driver input every tick and commands. Handlers of Fabric packet types run on the server thread.
- `sender.sendPacket(PacketVehicleConfigs.of(PackParser.configs()));` — A joining player gets the server's vehicle configs, so the client's definitions match what the server simulates (the tank on the fuel bar, the speedometer and tachometer scales, the gearbox in the hints). Not for the singleplayer host, who shares the server's definitions.
- `vehicle.vehicle().hornInput(WrapperPlayer.of(player), packet.horn());` — The held horn rides on the input packet the driver sends every tick anyway (`PacketVehicleInput#horn`), so a press and a release reach the server within a tick and need no packets of their own.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/network/PacketVehicleConfigs.java

- `public record PacketVehicleConfigs(String json) implements FabricPacket` — The vehicle configs in force on the server, by id, as one JSON object; a few hundred bytes per vehicle. The client rebuilds its definitions with them (`PackParser#useRemoteConfigs`) and with its own again after disconnecting (`SlimVehicleClient`). Ids the client does not know are ignored.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/network/RadioServer.java

- `final class RadioServer` — The server's radio: the folder `slimvehicle/radio` in the server directory (`RadioLibrary`) is listed every `CHECK_TICKS` in the background, and every player with the mod gets its stations (`PacketRadioLibrary`) on joining and again whenever the folder changes, then downloads the tracks it lacks by asking for them chunk by chunk (`PacketRadioRequest`, `PacketRadioChunk`). An admin only copies files into the folder; no restart, no resource pack. The same folder in a client's game directory is the player's own music (`RadioStations`), hence `FOLDER` and `README` are shared.
- `private static boolean receives(MinecraftServer server, ServerPlayer player)` — Not the singleplayer host: their integrated server reads their own radio folder, which the client already plays merged with resource packs. LAN guests get the host's folder. Players without this version of the mod (no channel) keep their own music.
- `if (!files.isEmpty() && receives(server, handler.getPlayer())) sender.sendPacket(library);` — A server without music sends nothing on join, so players keep their own music and resource packs there. When the folder changes the new list goes to everyone, an empty one included, so emptying the folder hands the players their own music back.
- `private static final int PER_PLAYER = 8;` — Chunks served per player per tick, and `PER_TICK` for all players together (12 × 32 KiB = 384 KiB a tick, about 7.5 MiB/s). The client keeps at most `RadioDownloads#WINDOW` requests open, so a slow connection gets only as much as it takes and game packets are not stuck behind a queue of music. Players are served in turn: whoever was served is moved to the end of `QUEUES`.
- `private static final int QUEUE = 64;` — Requests beyond this per player are dropped; an honest client has eight open.
- `private static PacketRadioChunk chunk(PacketRadioRequest request)` — Reads the chunk from disk when it is sent, on the server thread (32 KiB reads, mostly from the OS cache). Only hashes of the current list are served, so a client cannot ask for any other file. A file that shrank since it was listed sends nothing; one that changed in place fails the client's hash check, and the next listing has its new hash.
- `ServerLifecycleEvents.SERVER_STARTING.register(server -> {` — The state is static and an integrated server starts again in the same game: clear the queues, and a scan whose result was meant for a stopped server would leave `checking` set forever.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/network/PacketRadioLibrary.java

- `public record PacketRadioLibrary(List<RadioLibrary.Station> stations) implements FabricPacket` — The server's stations: per track its title, hash, size and length. About a hundred bytes a track, far below the 1 MiB a custom payload may carry. Tracks read from it have no `file`.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/network/PacketRadioRequest.java

- `public record PacketRadioRequest(String hash, int offset) implements FabricPacket` — One chunk of one track: its SHA-1 (`HASH` hex characters) and the byte offset, a multiple of `PacketRadioChunk#SIZE`.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/network/PacketRadioChunk.java

- `public static final int SIZE = 32 << 10;` — 32 KiB a chunk: small enough to sit between game packets without holding them up, large enough that a 5 MB track needs only 160 packets. Vanilla compresses every packet; Ogg does not shrink, which costs the netty thread some time but nothing else.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/command/SlimVehicleCommand.java

- `Commands.argument("vehicle", StringArgumentType.greedyString())` — Vehicle ids contain a colon, which a single-word argument does not accept.
- `private static int radio(CommandContext<CommandSourceStack> context)` — `/slimvehicle radio`: the stations the server sends (`RadioServer#stations`) with their track counts and lengths, so an admin sees that the files were picked up. Open to everyone, like `packs`.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/VehicleControls.java

- `class VehicleControls` — Key mappings and the driver's input, sent every tick while seated.
- `public static boolean handbrakeHeld()` — Local handbrake key state for the dashboard lamp (the server does not echo it back).
- `public static String keyName(VehicleAction action)` — The key bound to an action as the HUD shows it, so the hints follow the player's own bindings. `handbrakeName` and `vibrationsName` for the keys that are not actions.
- `private static void lockFromOutside(Minecraft mc)` — The lock key on foot: the car under the crosshair, else the nearest car within `ItemKey#REACH` that a key in the inventory opens (the client knows its own items and the cars' UUIDs). With neither it says why in the action bar.
- `private static Set<UUID> keys(LocalPlayer player)` — The cars the local player's keys open, every slot including the off hand. `holdsKey` asks it for the HUD (the blinking key lamp and the "no key" hint).
- `public static boolean hornHeld()` — The horn key as the input packet reports it; the HUD lights the horn hint from it.
- `public static String useKeyName()` — The use key (right mouse button by default) as the HUD prompt shows it in its key cap, from the player's bindings. `forwardName` is the forward key, for the "drive" hint of an automatic.
- `private static String label(KeyMapping key)` — What a key cap shows. Letter, digit and punctuation keys by their physical key (`key.keyboard.r` → R, `SYMBOLS` for punctuation): vanilla names them by the active keyboard layout, so with a Russian layout the caps read К, Р, Я, Ч next to labels in the game's language, which looked untranslated. Named keys (Shift, arrows, mouse buttons) keep vanilla's name in the game's language.
- `radioNext = key("radio_next", GLFW.GLFW_KEY_PERIOD);` — Radio stations: `.` next, `,` previous (on a Russian layout the Ю and Б keys); neither vanilla nor TaCZ binds them.

### VehicleControls.tick(Minecraft)

- `if (vehicle.vehicle().pushOut(WrapperEntity.of(player))) break;` — The client decides where its own player moves, so it is the side that pushes the player out of a vehicle they are caught in (`AEntityE_Interactable#pushOut`).

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/packs/FolderPackSource.java

- `class FolderPackSource` — Folder and zip packs as client resource packs, so their textures, sounds and translations load; models and animations are read from the packs directly (`PackParser#openAsset`). They are required and on top, so they are always active and override nothing they do not contain. Added to the client's pack repository by `PackRepositoryMixin`.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/DriverPose.java

- `class DriverPose` — The seated driver: the arms on the wheel and lever (both camera modes), and the first-person view. From the driver's seat the player sees their own body (torso, legs and arms, not the head), and the camera sits at the eyes of a head turning on its neck: looking down it moves forward over the chest, looking over a shoulder it leans towards that side. So the view is limited only like a real neck, not to keep the ends of floating arms out of sight as before.

### DriverPose

- `static final float LOOK_YAW = 150;` — First-person look range relative to the car: to either side (yaw) and up (negative pitch, LOOK_UP) / down (LOOK_DOWN).
- `private static final double EYE_UP = 4.5;` — Eye position in the head, model pixels: above the neck joint (EYE_UP) and in front of it (EYE_FORWARD).
- `private static final double EYE_BEHIND = 1;` — The eye never goes further behind the neck than this (model pixels): the back of the torso and the seat are there.
- `private static final double LEAN_OUT = .15;` — Looking far back the driver turns the shoulders and leans out this far (blocks) to see past the headrest.
- `public static boolean firstPersonDriver(Entity entity)` — Whether the player is the first-person driver of a vehicle with a seat to sit in and a wheel.
- `public static void clampLook(Entity entity)` — Called after every mouse turn and once per tick (the car turns the view with it, the camera mode can change).
- `private static Vec3 modelOrigin(EntityVehicleF_Physics vehicle, float partial)` — Where the seated rider's model stands (feet, before the lean) at this frame.
- `private static void place(PoseStack pose, EntityVehicleF_Physics vehicle, float partial)` — Model transform of the seated rider, the same for the camera and the first-person body.
- `public static Vec3 eye(Entity player, float partial)` — First-person eye of the driver: the neck joint of the rendered body plus the eye offset turned with the view, held in front of the seat and leaning out when looking back.
- `public static void faceForward(ModelPart head, ModelPart hat)` — Seated riders face the road: the head follows the body (and the car), not the orbiting third-person camera.
- `public static void renderFirstPerson(WorldRenderContext context)` — The driver's own body in first person: everything but the head, posed like the third-person model (riding legs, hands on the wheel), in the same place the camera's neck is.

### DriverPose.clampLook(Entity)

- `entity.yRotO += yaw;` — Shift the previous rotation too, so the clamp never shows up as a one-frame swing.

### DriverPose.aim(EntityVehicleF_Physics,ModelPart,Point3D)

- `arm.xRot = (float) Math.atan2(z, Math.sqrt(x * x + y * y));` — ModelPart uses Rz * Ry * Rx; aim its +Y (shoulder to palm) at the target.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/render/VehicleAnimations.java

- `class VehicleAnimations` — Animates any vehicle from its definition: body pose and vibration, wheels (`steer_`/`roll_`/`tyre_` + wheel bone suffix), hinged parts, windows, wipers, steering wheel and gear lever. Written into the frame's copy of the `Animator` pose after the JSON animations, so code wins where both drive a bone, as it did over GeckoLib's controllers.
- `static void apply(EntityVehicleF_Physics vehicle, JSONVehicle definition, float partial, Animator.Pose pose)` — Values replace the channel (as GeckoLib's `setRotX` did), and the frame's copy starts again from the animator's pose every frame, so a bone that stops moving returns to where the animations put it.

### VehicleAnimations.apply(EntityVehicleF_Physics,JSONVehicle,float,Animator.Pose)

- `float buckle = opening.isJammed() && length > 0 ? (float) (opening.definition.buckle / length) : 0;` — A part bent in a crash (jammed) stands slightly proud of the body: its free edge sticks out.
- `float swing = open + buckle;` — Only the hinge axes are driven; the others keep the rotation the model gives the bone.

### VehicleAnimations.wipers(EntityVehicleF_Physics,JSONVehicle,float,Animator.Pose)

- `double angle = Math.toRadians(wipers.glassAngle);` — Rotate about the windshield normal, preserving the imported blade mesh.

### VehicleAnimations.driver(EntityVehicleF_Physics,JSONVehicle,float,Animator.Pose)

- `Point3D axis = driver.steering.axis;` — The wheel spins about the definition's axis in the steering bone's own frame (the model's tilt stays on its parent bone).
- `int gates = (definition.gears() + 1) / 2;` — An H-pattern: odd gears forward, even back, two gears per gate from left to right; reverse at the far right, back. Neutral rests in the first gate, as in 0.1.9.
- `if (definition.transmission.automatic) {` — An automatic's selector is a straight gate: R forward, N in the middle, D back, whatever gear the box has picked.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/render/VehicleRenderer.java

- `class VehicleRenderer` — Draws vehicles from their `ModelGeometry` and the frame's pose, replacing GeckoLib's renderer step for step (bone and cube transforms, normals, flat-cube fix, vertex order), so the models look as they did; checked against GeckoLib 4.4.4 over all bundled models in several poses. Up to three passes per vehicle: every opaque cube into the shared cutout batch, then only the panes into the translucent batch, then the glowing parts of `lamps.glow` additively. Switching buffers per bone used to flush the batch and push the rest of the body into the translucent buffer, which was depth-sorted every frame for every car. Lamps stay in the cutout pass and are lit full-bright instead. Bones under the definition's "dent" bones are drawn bent by the vehicle's dents (`DentMesh`).

### VehicleRenderer

- `private static final double INTERIOR_LOD = 32 * 32;` — Interior detail is skipped for cars far from the camera; it is a few pixels through tinted glass there.
- `Map<ResourceId, Set<String>> GLASS_BRANCHES` — Bones that hold glass or lead to it, per model: the pane pass walks only these. `GLOW_BRANCHES` the same for the glow bones.
- `private static final float TAIL_GLOW = .45f;` — Glow of a `tail` lamp with the lights on, as a share of its brake-light brightness (as `VehicleLights#TAIL_GLOW`).
- `draw(builder, pass, pose, buffers.getBuffer(VehicleLights.active() ? VehicleRenderTypes.glass(texture) : RenderType.entityTranslucent(texture)), partial, light);` — While the light pass runs, panes are drawn without writing depth. The pass lights what the depth buffer shows; with depth the windshield was the nearest surface for every pixel seen through it, so from inside the car the road ahead showed no headlight beam at all. Without the pass vanilla's type is kept: depth keeps water, clouds and particles behind the glass sorted.
- `if (lamps != null && !lamps.glow.isEmpty() && !(vehicle.lampBroken(EntityVehicleF_Physics.TAILLIGHT_LEFT) && vehicle.lampBroken(EntityVehicleF_Physics.TAILLIGHT_RIGHT))) {` — Glow pass: the glow bones drawn again with `RenderType.eyes` over a mask of the texture that keeps only lamp-red texels (`LampGlow`), so only the lamp part of a cube lights up (the red strips of the G 63's bumper, not the whole bumper block). It is additive, so black adds nothing. Gone once both tail lamps are broken.
- `private static float glowLevel(Pass pass, String bone)` — Brightness of a glow bone in this frame: `brake` lamps only when braking, `tail` lamps at `TAIL_GLOW` with the lights and full when braking.
- `pass.flash = vehicle.lockFlash(builder.level().getGameTime());` — Indicator bones light with the lock flash too (`AEntityVehicleE_Powered#lockFlash`).
- `boolean led = ClientConfig.led(lamps);` — The headlight bones glow cold white for LED lamps and warm for halogen, matching the beam.
- `Set<ResourceId> BROKEN_MODELS` — Models that failed to load; logged once and skipped (the vehicle is invisible but works) instead of throwing every frame. Cleared with the caches on a resource reload.
- `private final Map<Animator, Animator.Pose> shown = new WeakHashMap<>();` — The pose actually drawn: the animator's pose copied every frame, with `VehicleAnimations` written over it. Kept per animator, so its arrays are not allocated every frame.
- `animator.update(time, time / 20, vehicle::animationActive);` — Animations advance with the vehicle's ticks plus the frame's partial tick, as GeckoLib's did; `query.life_time` gets the same time in seconds.
- `pose.translate(0.0F, 0.01F, 0.0F);` — GeckoLib lifts every model by 0.01 so the bottom faces do not z-fight with the ground; kept, or the tyres would flicker into the road.
- `pose.translate(-values[POSITION] / 16.0F, values[POSITION + 1] / 16.0F, values[POSITION + 2] / 16.0F);` — Bone transform in GeckoLib's order: animated offset (X negated), pivot, rotation Z then Y then X (each only when non-zero, so the floating-point result matches), scale, back from the pivot.
- `if (!pass.pose.hideChildren[bone.index]) {` — A hidden bone (a lowered window, a broken pane) hides its children too, as in GeckoLib.
- `static void cubeTransform(PoseStack pose, ModelGeometry.Cube cube)` — A cube's own rotation about its pivot, one axis per quaternion like GeckoLib. Also used by `DentMesh`.
- `static void fixInvertedFlatCube(ModelGeometry.Cube cube, Vector3f normal)` — GeckoLib's fix for zero-thickness cubes (planes such as glass and decals): a normal pointing into the flat side is flipped so both sides are lit alike.
- `private static void hinge(PoseStack pose, ModelGeometry.Bone bone, float[] values, EntityVehicleF_Physics vehicle)` — A part that opens (door, lid, flap) turns about its hinge as moved by the dents, not about the undented hinge. The bent mesh is worked out in the closed pose, so a part over a dent sinks in with the body; turning it about the undented hinge, which then lies outside the bent body, swung it open in the air beside the car (most visibly the fuel flap on a crushed tail). With the hinge pushed by p and the part turned by R, the pose after the bone transform moves by R⁻¹p − p: nothing while closed, and the hinge stays on the bent body as it opens. The translation also carries the part's child bones (its glass).

### VehicleRenderer.drawBone(Pass,PoseStack,ModelGeometry.Bone,int,int,float,float,float,float)

- `if (pass.branch != null && !pass.branch.contains(name)) return;` — The pane and glow passes only walk the branches that hold their bones.
- `} else if (vehicle.flag(EntityVehicleF_Physics.LIGHTS) && definition.lamps != null && definition.lamps.frontIndicator != null && name.startsWith("indicator_front")) {` — Front indicator lenses double as position lamps, like the white lamps on top of the G 63's wings: with the lights on they glow full-bright in the headlight tint (cold for LED, warm for halogen) and blink amber over it when signalling. `VehicleLights#smallLamps` adds their faint light around the wings.

### VehicleRenderer.drawCubes(Pass,PoseStack,ModelGeometry.Bone,int,int,float,float,float,float)

- `if (!vehicle.dents().isEmpty() && definition.openingsByBone.containsKey(name)) hinge(pose, bone, pass.pose.values[bone.index], vehicle);` — Before anything else, so the pane pass moves the part's glass the same way.
- `List<ModelGeometry.Cube> cubes = bone.cubes;` — A broken lens stays dark and unlit; the other side keeps glowing.
- `if (pass.glow) {` — After the hinge, so a glow bone under an opening (the G 63's third brake light on the tailgate) swings with it; the cubes are drawn white at the glow level and the mask colours them.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/render/VehicleRenderTypes.java

- `final class VehicleRenderTypes extends RenderType` — Render types of the vehicle renderer. A subclass because `RenderType.CompositeState` and the texture shard are protected; it is never instantiated.
- `private static final Function<ResourceLocation, RenderType> GLASS = Util.memoize(texture -> create("slimvehicle_glass",` — Vanilla's `entity_translucent` (same shader, blending, no culling, lightmap, overlay, sorting) with `COLOR_WRITE` only: panes that do not hide what is behind them from the light pass.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/render/LampGlow.java

- `final class LampGlow` — Glow masks of vehicle textures for `lamps.glow`: a copy of the texture that keeps lamp-red texels, brightened to full red, and turns every other texel black. Built on first use per texture as a dynamic texture and dropped on a resource reload. A texture that cannot be read is logged once and glows nowhere.
- `static boolean lamp(int red, int green, int blue, int alpha)` — A lamp texel: opaque, red above LIT and at least DOMINANCE times green and blue. The G 63's third brake light and bumper strips pass; its white, grey and black paint, the carbon bonnet and chrome do not.
- `float gain = 255f / red;` — Normalised to full red so a dark red lens still glows clearly; green and blue keep their share, so an orange lens stays orange.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/render/DentMesh.java

- `class DentMesh` — The bent surface of one vehicle's model for its current dents. Faces a dent reaches are cut into a grid of small quads and pushed in around the dent centre, a little unevenly so the metal looks crumpled, and darkened where the paint is scraped; their normals follow the bent surface, so the dents catch the light. Shapes are worked out once in the model's rest pose, then drawn every frame through the bone matrices, so dents move with doors and lids. Faces no dent reaches are drawn as they are.

### DentMesh

- `private static final double CELL = .11;` — Grid spacing (model units, about two model pixels); MAX_CUTS caps the cuts per edge.
- `private static Point3D chassis(float x, float y, float z)` — Model space mirrors chassis X and Z (see `Hull`).
- `private record Patch(int cols, int rows, float[] position, float[] uv, float[] shade, float[] normal)` — A face cut into cols×rows cells: corner positions (rest model space), texture coordinates and paint shade per grid point, a normal per cell.
- `void renderCube(PoseStack pose, ModelGeometry.Cube cube, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha)` — Draws one cube of a dented bone. pose is the bone's matrix, before the cube's own rotation.
- `private boolean reached(float minX, float minY, float minZ, float maxX, float maxY, float maxZ)` — Whether any dent reaches a box of rest model space.

### DentMesh.renderCube(PoseStack,ModelGeometry.Cube,VertexConsumer,int,int,float,float,float,float)

- `for (int corner = 0; corner < 4; corner++) {` — Same corner order as the face: (0,0) (1,0) (1,1) (0,1).

### DentMesh.build(ModelGeometry.Cube,ModelGeometry.Quad,Matrix4f)

- `int cols = cuts(v[0], v[1]);` — Cuts per edge follow the length of the cube's axis, so faces sharing an edge cut it alike and the bent cube stays closed.
- `corner[0].lerp(corner[1], s, a);` — Bilinear over the face: v0→v1 along s, v0→v3 along t.
- `Point3D push = DamageModel.displacement(dents, chassis(at.x, at.y, at.z));` — The same displacement as the hull and the hinges of dented parts, so they stay where the bent mesh is.
- `shade[i] = 1 - .38f * Mth.clamp((float) push.length() / .2f, 0, 1);` — Scraped paint and shadowed creases darken the deepest metal.
- `Vector3f flat = new Matrix3f(rotation).transform(new Vector3f(quad.nx(), quad.ny(), quad.nz()));` — Normals of the bent cells, on the side the flat face pointed to.

### DentMesh.reached(float,float,float,float,float,float)

- `double x = Mth.clamp(dent.pos().x, -maxX, -minX);` — The box in chassis axes: X and Z flip sign.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/light/LightVolume.java

- `class LightVolume` — Blocks around the camera, read by both lamp passes on the GPU. One RGBA texel per block. R is the occupancy: 0 lets light through (air, glass, plants, fences), 1..16 is solid from the bottom up to n/16 of the block (16 = full cube, 8 = bottom slab or stairs, 2 = snow layer), 17..32 is solid from (n-16)/16 to the top. G and B are the block's block light and sky light (0..15), so the light pass knows how the frame lit each surface. Layers are stored as tiles of one texture. A few layers are read per tick into the CPU copy and the texture is replaced once the whole volume is done, so block and light changes show within half a second.

### LightVolume

- `static final int SIZE_X = 96;` — SIZE_*, TILES and BELOW_CAMERA: keep in step with vehicle_light_shadow.fsh and vehicle_light.fsh.
- `private static final double LEAD = 12;` — Ticks of travel the volume is centred ahead of the camera, so beams in front stay inside it.
- `private static final double GAP = 3.5 / 16;` — A column of a shape still counts as solid from the bottom (or top) across a gap up to this, so a bed or cauldron on legs keeps its body.
- `private static final double COVER = .7;` — Share of the footprint that must be solid up to a height for the block to shade up to it.
- `static byte classify(List<AABB> boxes)` — Occupancy from a block's shape: the height solid from the bottom (else the thickness solid from the top) in at least COVER of an 8×8 grid of columns. Stairs come out as a bottom slab, a path or farmland as 15/16, fences and other narrow shapes as nothing. Using the bounds of the shape, as before, made stairs a full cube, so their lower step sat in its own shadow.
- `private static byte[] skyColumns(LayerLightEventListener sky, SectionPos at, BlockPos.MutableBlockPos pos)` — Sky light of a section that has no data layer of its own: the light engine then takes the bottom of the next section above that has one, so the value is the same at every height of the section. Read once per column per volume instead of once per block.

### LightVolume.tick(Minecraft,boolean)

- `int layers = origin == null ? SIZE_Y : LAYERS_PER_TICK;` — The first volume after the lamps come on is read at once; later ones a few layers per tick.

### LightVolume.fill(ClientLevel,int)

- `DataLayer blocks = chunk == null ? null : blockLight.getDataLayerData(at);` — Light levels come straight from the sections' data layers: a lookup through the light engine for each of the ~37 000 blocks read per tick costs too much.

### LightVolume.kind(ClientLevel,BlockState,BlockPos)

- `if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WALLS)) kind = 16;` — Leaves and walls shade like full cubes. A wall covers less than COVER of its block, but a beam must not pass through it.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/light/VehicleLights.java

- `class VehicleLights` — Every lamp of every nearby vehicle is one light source, composited into the frame in a single pass: the light it throws on surfaces (shadowed by blocks, other vehicles and the people and animals in its beam), its glow in the night air and the bloom of a lens that faces the camera all come from the same beam. Shadow maps for the headlights are traced on the GPU through `LightVolume` every frame; lamps without a shadow map (small lamps, fires, headlights past the shadowed ones) are tested against the same volume per pixel, and the volume's light levels tell the pass how the frame already lit each surface.

### VehicleLights

- `static final int MAX_LAMPS = 32;` — MAX_LAMPS, MAX_OCCLUDERS, MAX_SHADOWED, MAP_WIDTH and MAP_HEIGHT: keep in step with vehicle_light.fsh and vehicle_light_shadow.fsh.
- `private static final Point3D FORWARD = new Point3D(0, 0, 1);` — Lens centres come from the definition ("lamps", left side, mirrored for the right).
- `private static final float TAIL_RANGE = 8;` — Range of the small lamps in blocks (tail, indicator, reverse); headlights use the shader's RANGE. The reference mods throw a red patch some four blocks behind the car; 5.5 with the old weak lamp left a faint smudge.
- `private static final float TAIL_GLOW = .45f;` — Tail lamps with the lights on, as a share of a brake light.
- `static final int MAX_OCCLUDERS = 16;` — Boxes the shadow maps trace: up to `MAX_VEHICLES` car bodies, then people and animals (`MAX_FIGURES`).
- `private static final double FIGURE_SHRINK = .1;` — Figures cast shadows from a box a little narrower than their hit box, so their own lit front is not taken for shadow.
- `private static final float CLEAR_HAZE = .008f;` — Air glow of the headlights on a clear night: the beams show as soft cones in the air, as in the reference videos, and grow stronger in rain. At .005 with the old phase function only a streak at the lens showed.
- `private static final float[] HALOGEN = {1, .87f, .68f};` — Beam colours: halogen warm and yellowish, `LED` cold white. Which one a car gets: `ClientConfig#led`.
- `private static final float FIRE_RANGE = 11;` — Range of the light a fire throws around, blocks.
- `private static final int OMNI = -2;` — Slot value that marks a light shining every way (vehicle_light.fsh).
- `FloatBuffer DATA` — Lamp rows (position+type, direction+shadow slot, colour+vehicle, right+light levels), then occluder boxes (centre+yaw, half size+vehicle id, -1 for figures).
- `private static final class Levels` — Smoothed lamp output: filaments take a moment to glow up and to fade.
- `public static boolean register()` — The pass exists only with `lights=full` in `slimvehicle-client.properties` (`ClientConfig`): with `off` its shaders are not even registered and `SlimVehicleClient` does not hook the render event. A vehicle loses nothing without it; lamps still glow through their textures (`VehicleRenderer`).
- `public static boolean active()` — Also asked by `VehicleRenderer`, which draws glass without depth while the pass runs. Asked every tick and frame: with an Iris shader pack in use the pass is skipped (the pack has its own depth and colour buffers, so reading the frame here would draw garbage) and `LightVolume` stops reading blocks. Turning the pack off brings the pass back without a restart.
- `private static float night(float daylight, int sky, int block)` — How much the lamps show at a place with these light levels: 0.07 in full daylight up to 1 in the dark.
- `private static boolean glows(ClientLevel level, EntityVehicleF_Physics vehicle, float daylight)` — Whether a vehicle needs `LightVolume`: headlights or a fire, or other lamps lit where it is dark enough for them to show (night factor of its cabin above DARK). Brake lights come on all day in traffic; reading the volume for them in daylight costs CPU for nothing visible. The indicator flash of the lock counts as a lamp too.
- `private static float haze(WorldRenderContext context, ClientLevel level, float partial)` — Scattering density of the air: a little on a clear night, more in rain and storms, none under water.
- `private static int figures(ClientLevel level, Camera view, float partial, int occluders)` — The nearest people and animals as boxes in the headlights' shadow maps, so they cast shadows on the road like in the reference mods. Riders are left out: they sit inside a car body that already casts one. So is the camera's own player in first person (`self`): its body is not drawn there, yet its box cut a dark stripe through the beam and the haze, and walking near the car swept that stripe across both lamps, which looked like the beam itself jerking about. In third person the body is drawn and keeps its shadow.
- `private static final float POSITION_GLOW = .35f;` — Faint white (LED) or warm (halogen) light of the position lamps at `lamps.front_indicator`, range `POSITION_RANGE` blocks, while the lights are on. It dims while the same lens blinks amber, so the two do not add up.
- `private static void fire(EntityVehicleF_Physics vehicle, float night, int light, float partial, Point3D camera)` — A burning vehicle lights its surroundings deep orange from just above the flames, flickering with them: one light over the engine bay, and one over the middle of a vehicle that burns all over. A yellower light made grass glow yellow-green.
- `private static void add(EntityVehicleF_Physics vehicle, Point3D local, Point3D forward, Point3D right, float type, int slot, float r, float g, float b, int light, float partial, Point3D camera)` — A small lamp in the vehicle's chassis frame: it follows the body's pitch and roll.
- `private static void emit(EntityVehicleF_Physics vehicle, Point3D at, Point3D f, Point3D side, float type, int slot, float r, float g, float b, int light, Point3D camera)` — Writes one lamp row from a world position and world directions.
- `private static int headlights(EntityVehicleF_Physics vehicle, float k, boolean shadow, int light, float partial, Point3D camera)` — Headlights sit on the body but aim by `levelledDirection`: self-levelling, so squat and dive no longer lift the beam off the road. Each lamp that gets a shadow map takes the map of its own row (`slot` = the row index); returns how many it took. Before, a car with one broken headlight gave its remaining lamp the map of the slot after it, which the shadow pass traced from whatever lamp held that row (a tail lamp, facing backwards): the lit patch ended in a straight shadow edge from the car's nose.

### VehicleLights.night(float,int,int)

- `float ambient = Math.max(daylight * sky / 15f, block / 15f * .8f);` — Lamps matter at night, in tunnels and under roofs; in daylight they barely add anything.

### VehicleLights.render(WorldRenderContext)

- `boolean[] traced = new boolean[vehicles.size()];` — Shadow-casting headlights first, so a lamp's index is also its shadow-map slot.
- `Vector4d eye = new Matrix4d(viewProjection).invert().transform(new Vector4d(0, 0, 1, 0));` — Where the eye really is in camera-relative space, for the shader's `Eye`. Vanilla puts view bobbing (and the hurt shake) into the projection matrix as a small translation, so while walking the eye sways up to about 0.1 block around the camera position everything else is relative to. The eye is the point a perspective matrix sends to clip x = y = w = 0, which is the inverse applied to (0, 0, 1, 0); computed in double. Before `Eye` the haze cone and the lens glare were traced from the unbobbed camera while the frame was drawn from the bobbed eye, so with every step the cone slid against the lamp it comes out of, by some 16 pixels at a few blocks and far more right at the lens.

### VehicleLights.headlights(EntityVehicleF_Physics,float,boolean,int,float,Point3D)

- `for (int side = 0; side < 2; side++) {` — Dipped beam in the colour of the lamp type (`HALOGEN` or `LED`).

### VehicleLights.smallLamps(EntityVehicleF_Physics,Levels,float,int,float,Point3D)

- `float tail = Math.max(TAIL_GLOW * levels.tail, levels.brake) * night;` — Brake lights are the same red lamps, brighter.
- `float position = POSITION_GLOW * levels.tail * night * (1 - Math.min(1, indicator / amber * 2));` — The position lamp follows the tail lamps' smoothed level (both come on with the lights) and gives way to the amber blink.

### VehicleLights.fire(EntityVehicleF_Physics,float,int,float,Point3D)

- `float k = Math.max(night, .2f) * strength * flicker * 1.6f;` — A fire shows by day too, though much less than at night.

### VehicleLights.update(EntityVehicleF_Physics,float)

- `boolean flash = vehicle.lockFlash(vehicle.world().gameTime());` — The lock flash lights both indicators like a turn signal.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/light/LightGL.java

- `final class LightGL` — Every OpenGL and `RenderSystem` call of the light engine: shader registration, data textures, uploads, the shadow-map and scene-copy targets, the pass state and the full-screen quad. `VehicleLights` and `LightVolume` only compute what to draw. Moving to a game version with another renderer (1.21.5+) rewrites this class and the shader registration; the `.fsh` maths carries over largely as is.
- `static void registerShaders(Consumer<ShaderInstance> light, Consumer<ShaderInstance> shadow)` — Core shaders through Fabric's registration callback, so they reload with resource packs like vanilla's.
- `private static void nearest()` — Data textures are read with texelFetch: no mipmaps, so the minifying filter must not use them.
- `private static void unpack()` — Vanilla texture uploads leave row length and skips set; tightly packed uploads need them cleared.
- `static TextureTarget copyOf(TextureTarget current, RenderTarget main)` — A copy of the frame's colour and depth, which the light pass reads while it draws into the frame itself. Resized with the window.
- `if (depthFormat(current) != depth) reformatDepth(current, depth);` — The copy's depth always takes the frame's depth format before the blit. `glBlitFramebuffer` with the depth bit fails with `GL_INVALID_OPERATION` when the two depth or stencil formats differ, and then copies nothing, colour included. TaCZ causes exactly that: the first time a scope or red dot is drawn (a gun with one in hand, `RenderHelper#enableItemEntityStencilTest`), it rebuilds the main target's depth as `DEPTH32F_STENCIL8` for its lens stencil and keeps it for the rest of the session. The pass then read a stale or empty scene whose depth looked like open sky everywhere, so the headlights' haze ran 96 blocks through the bonnet. The screen went white the moment a lamp lit, typically when the driver switched the headlights on or braked at night.
- `private static int depthFormat(RenderTarget target)` — The internal format of a target's depth texture, asked every frame the pass runs. Others rebuild both targets (window resizes, TaCZ's stencil switch), and a rebuilt texture can get the same id back, so neither the ids nor the format can be cached. It costs two state queries per lit frame, next to a full-frame blit.
- `private static void reformatDepth(RenderTarget target, int format)` — Reallocates the depth texture in the given format, keeping its id and sampling parameters. A packed depth-stencil texture is attached as the stencil too, as the main target has it, so the blit sees identical buffers; any other format clears the stencil attachment. The shader still reads `.r` as depth, since a depth-stencil texture samples its depth by default.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/light/IrisCompat.java

- `final class IrisCompat` — Whether an Iris shader pack is in use, through Iris's public API by reflection, so the mod neither depends on Iris nor breaks without it. An Iris whose API cannot be found is logged once and treated as no shader pack.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/light/DynamicLightsCompat.java

- `public class DynamicLightsCompat implements DynamicLightsInitializer` — LambDynamicLights support, loaded through its `dynamiclights` entrypoint only when that mod is installed. A vehicle with its headlights on or on fire becomes a moving light source for the block lighting, which also works with shader packs.
- `if (VehicleLights.active()) return 0;` — With the light pass running the lamps already light the scene; adding LambDynamicLights' block light on top would light everything twice.
- `private static final int HEADLIGHTS = 12;` — Block-light level around a car with both headlights; one broken lamp gives 10, a fire up to 15 with its strength.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/ClientConfig.java

- `class ClientConfig` — `config/slimvehicle-client.properties`: `vibrations` (cosmetic body shake; the suspension always works), `lights` (`full` or `off`, the light pass), `headlights` (`pack`, `halogen` or `led`) and `hints` (the key hints beside the dashboard). Read once at client start; a file missing a key is rewritten with the current values, so new keys appear in old files.
- `public static boolean led(JSONVehicle.Lamps lamps)` — Whether a car's headlights are cold white LEDs: its own `lamps.headlight_type` with `headlights=pack`, otherwise the player's choice for every car.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/SlimVehicleClient.java

- `boolean lights = VehicleLights.register();` — After `ClientConfig.load()`: whether the light pass exists is decided once, before the render events are hooked.
- `VehicleRenderer.registerReload();` — Model and animation caches are dropped on every resource reload (F3+T), so edited pack files show without a restart. `VehicleHud.registerReload()` does the same for the built dial faces and damage diagrams.
- `ClientPlayNetworking.registerGlobalReceiver(PacketVehicleConfigs.TYPE, (packet, player, sender) -> PackParser.useRemoteConfigs(packet.configs()));` — The server's vehicle configs on joining; its own again on leaving (`DISCONNECT`, on the client thread).
- `WorldRenderEvents.START.register(CabinAcoustics::observe);` — At the start of the frame the camera is already placed for it, and the cabin test sees camera and car at the same partial tick.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/render/VehicleShadow.java

- `class VehicleShadow` — One soft footprint per vehicle, clipped to nearby block tops like vanilla entity shadows.

### VehicleShadow.render(WorldRenderContext)

- `List<Vec3> polygon = new ArrayList<>(List.of(new Vec3(top.minX, top.maxY + .008, top.minZ), new Vec3(top.maxX, top.maxY + .008, top.minZ),` — Clip block-top rectangles to the oriented footprint, keeping UVs in [0,1].

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/sound/VehicleAudio.java

- `class VehicleAudio` — A looping vehicle sound that follows the vehicle and sets its own volume and pitch every tick: an engine layer by rpm (`JSONSounds.EngineLayer`) outside at the engine bay or its cabin recording inside, siren, radio, road roll and tyre squeal. It belongs to the definition it was started for and stops when the vehicle's definition changes, so a car that was still the default Volga for a moment never keeps the Volga's engine.
- `ENGINE_INSIDE(EntityVehicleF_Physics.ENGINE),` — A layer's cabin recording: played without position (relative, no attenuation) at the share `CabinAcoustics#inside` gives, while the outside layer takes the rest; it stops once silent, so cars nobody sits in do not hold channels for it.
- `public boolean muffled(EntityVehicleF_Physics cabin)` — Whether the cabin's low-pass filter applies: everything but the cabin recordings and the radio of the car one sits in.
- `HORN(EntityVehicleF_Physics.HORN),` — The horn loop (`horn.ogg`, a seamless half second) plays while the flag is on and stops the tick it goes off, at full volume at once (no fade in): a horn has no attack to smooth. It sounds from the engine bay like the real one behind the grille.
- `public static VehicleAudio radio(EntityVehicleF_Physics vehicle, String sound)` — The radio loop. With music on this client (`RadioStations#tune`) it plays the station the car is tuned to from its place in the broadcast at this game time (`RadioStream`), through the `radio_station` sound (a stream, so the engine asks `getAudioStream`); without music, or while the track that is on now is still downloading from the server (`playable`), it is the vehicle's own `radio` sound as before.
- `public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, ResourceLocation id, boolean repeatInstantly)` — Fabric's hook for a sound's audio data (`FabricSoundInstance`). The station stream reads and seeks its first track, so it is made on the background executor, as vanilla loads streams.
- `|| vehicle.definition() != definition || kind == Kind.RADIO && (vehicle.radioStation() != station || retuned())) {` — A radio stops when the car is tuned to another station or what it should play changed; `VehicleEffects` starts the new one on the next tick.
- `private boolean retuned()` — After any change to the music (`RadioStations#generation`) a radio restarts only if the station it should play now differs from the one it plays: another track list, or music instead of the stand-in melody once the current track has downloaded. Finished downloads of other tracks, a new server list that leaves this station alone and a rescan of the player's folder while on a server keep it playing without a gap.
- `private static final float RADIO_SHUT = .12f;` — Radio of a closed car heard from outside: this share of its volume, and far duller (`CabinAcoustics#SHUT_HIGHS`). `shut` is how closed the cabin is (0 open .. 1 shut, from `CabinAcoustics#openness`) and 0 for the people inside, who hear it clear. Before, the G 63's radio played at full volume around a car with every door shut.

### VehicleAudio.tick()

- `if ((kind.flag == 0 || kind == Kind.ENGINE_INSIDE) && target < .001f) {` — Release rolling and skid channels when parked, and cabin recordings nobody hears. Outside engine layers stay available at zero gain so changing rpm does not restart their phase.
- `} else if (!kind.engine() || ++offTicks > spin) {` — A switched-off engine winds down over `spin_down`: the layers keep playing at the last revs falling to zero, crossfading towards idle, with pitch and volume going down together.
- `* (kind == Kind.ENGINE ? 1 - inside : inside) * (1 - fall * fall);` — `startFade` keeps the loops quiet under the start sound until the engine catches. The old 0.8 for a driver's own car is gone: the cabin filter and the cabin recordings do that now.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/sound/RadioStations.java

- `class RadioStations` — The radio's music on this client. On a server with music in its folder the stations are the server's (`useServer`, from `RadioDownloads`) and nothing else, so the station number every car syncs means the same music for everyone. Elsewhere (singleplayer, a server without music or without this version) they come from the player's folder `slimvehicle/radio` (`RadioLibrary`: loose files are the station "My music", every subfolder a station of its name) and from resource packs (`assets/<namespace>/sounds/radio/<station>/<track>.ogg`; files right in `sounds/radio` are a station named after the namespace), merged by name, "My music" first, then alphabetical. Tracks of a station play in file name order. Only Ogg Vorbis: the game has no other decoder, and LWJGL's stb_vorbis gives the seeking.
- `public record Track(String title, Path file, ResourceLocation resource, String hash, double seconds)` — A file of the player's folder, a resource of a pack, or (with `hash`) a track of the server, whose `file` is its place in the download cache whether or not it is there yet. So a station's tracks stay equal while they download, and `ready` asks `RadioDownloads` whether the file has arrived.
- `return Component.translatable(remote ? "radio.slimvehicle.server" : "radio.slimvehicle.own");` — The server's loose files are "Server radio", the player's own "My music".
- `public static Tuned tune(int counter, double time)` — What a car tuned to `counter` plays at `time` (game time in seconds): the station is the counter modulo the station list, and a station runs in a loop like a broadcast, shifted by a fixed offset per station name. Every client with the same files and the same synced game time hears the same song at nearly the same second, without syncing any track, and switching on lands in the middle of a song like a real radio. A server's tracks are timed by the lengths in its list, downloaded or not, so a player still downloading keeps the same schedule.
- `public static void tick(Minecraft mc)` — Every `CHECK_TICKS` the player's folder is listed in the background (`RadioLibrary#refresh`: name, size, date of every file); only a change that held still for one interval reads the files again and publishes new stations, so new music is picked up a few seconds after it is copied in.
- `private static void announce(Minecraft mc)` — The rider of a car with the radio on sees "Radio <station>: <track>" over the hotbar when the radio comes on, the station changes or the next track starts; while that track is still downloading from the server, "the track is downloading, N%" instead, updated as the percentage moves.
- `public static int generation()` — Counts changes to the music: publishes, and finished downloads (`changed`). A playing radio checks then whether it has to restart (`VehicleAudio#retuned`).
- `onResourceManagerReload(ResourceManager manager)` — Stations from resource packs, a server's pack included, are listed on every resource reload; their lengths are read in the background (`RadioLibrary#length`) and published on the main thread.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/sound/RadioStream.java

- `class RadioStream` — One station as an endless audio stream: decodes the tracks one after another with stb_vorbis, starts at the tuned track and offset, and wraps around. The output is always 44.1 kHz 16-bit mono: an OpenAL source's queued buffers must all have one format, the tracks of a station differ in rate and channels, and a mono source is the one OpenAL places in the world (the radio sounds from the car).
- `float value = previous + (current - previous) * (float) phase;` — Linear resampling to 44.1 kHz, carried across chunks and tracks so a change of track does not click.
- `if (!played) failures++;` — A track that opens but yields nothing (broken, or seeked past its end) counts as a failure; when every track of the station has failed in a row the stream ends instead of spinning.
- `private static final int FRAMES = 2048;` — Frames decoded per call.
- `if (!track.ready()) {` — A server track that has not downloaded yet plays as silence for its length (`waiting` counts the samples) instead of being skipped, so the station stays on the broadcast's schedule. Every `POLL` samples (0.1 s) it asks again; once the file is there the track opens at the second the silence has reached. Downloads go to the tuned station first, so in practice the next track is ready long before it starts.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/sound/RadioDownloads.java

- `final class RadioDownloads` — Brings the server's radio music to this client (`RadioServer`). The list (`PacketRadioLibrary`) becomes the station list at once (`RadioStations#useServer`); the tracks are fetched one at a time into `slimvehicle/cache/radio/<sha1>.ogg` in the game directory and play from there. Files are named by content, so a track already fetched on an earlier visit, or from another server, is not fetched again. Leaving the server drops the list and the player's own music is back.
- `private static final int WINDOW = 8;` — Requests open at a time, 256 KiB: each arriving chunk asks for the next one. The server sends no faster than the connection takes them, with at most a quarter megabyte of music ahead of the game's packets; at a 100 ms ping that is about 2 MiB/s.
- `private static RadioLibrary.Track next(Minecraft mc)` — What to fetch next: in a car, its station from the track that is on now onward, so the music one is waiting for comes first; then everything else in list order.
- `private static Set<String> cached(List<RadioLibrary.Station> stations, Set<String> hashes)` — In the background on each new list: tracks already in the cache with the right size count as ready, and their dates are renewed. Files that no list has named for `KEEP` (30 days), finished or partial, are deleted, so changing music on a server does not fill the disk.
- `if (index >= download.chunks || download.received.get(index) || packet.data().length != Math.min(PacketRadioChunk.SIZE, download.track.size() - packet.offset())) return;` — Chunks are written where they belong and counted once, so a late chunk of a cancelled try or a duplicate cannot corrupt or finish a file.
- `private static boolean verify(String hash)` — The finished `.part` is hashed in the background and only a match becomes the cache file (an atomic rename); anything else is deleted and retried, up to `TRIES` times per session.
- `private static final int STALL_TICKS = 200;` — Ten seconds without a chunk (the file left the server's folder, or the server dropped requests) gives up the try and moves on.
- `private static final Set<String> READY = ConcurrentHashMap.newKeySet();` — Hashes in the cache; read by the sound thread (`RadioStream`), so concurrent. It outlives a disconnect: a file in the cache stays valid for any server that names it.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/sound/VehicleCue.java

- `class VehicleCue` — A one-shot vehicle sound that follows the engine bay (the start sound), or plays without position when it is a cabin recording.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/sound/CabinAcoustics.java

- `class CabinAcoustics` — Sound insulation of a closed cabin. While the camera is in the cabin box of the car one rides (`JSONVehicle#inCabin`; first person, not the third-person view outside), every sound from outside the box goes through one OpenAL low-pass filter: quieter (`GAIN`) and much duller (`HIGHS`), scaled by the car's `cabin.muffle`. Sounds in the cabin (doors, lever, lock, voices), the radio and cabin recordings stay clear; engine sounds come from the engine bay (`AEntityVehicleE_Powered#enginePosition`), outside the box. The reference video's car cut the engine's high end like this from inside.
- `public static float openness(EntityVehicleF_Physics vehicle)` — How open the cabin is: open doors and the tailgate count most, the bonnet and hatches a little, lowered windows and broken panes in between. The filter fades out as the cabin opens, and the cabin recordings fade towards the outside ones.
- `public static float inside(EntityVehicleF_Physics vehicle)` — How much of the cabin recordings to play for this car: the smoothed closure while one sits in its cabin, else 0.
- `public static void observe(WorldRenderContext context)` — Whether the camera is in the cabin, decided at the start of every frame from the camera and the car of that same frame (`AEntityB_Existing#worldToLocal(Point3D, float)`); `tick` uses the last answer. Comparing the camera of the last frame with the car's new tick position, as before, put the driver's eye one to two ticks of travel behind the car: from about 120 km/h it left the G 63's cabin box, the filter and the cabin recordings switched off and the engine sounded as from outside; the third-person camera in front of the car fell into the box at speed and muffled everything.
- `private static void apply(Minecraft mc)` — Sets the filter on every playing channel each tick and takes it off everything once the camera leaves the cabin. OpenAL copies a filter's parameters when it is attached, so the changing strength is attached again each tick. The AL calls run on Minecraft's sound thread (`ChannelHandle#execute`).
- `private static final float SHUT_HIGHS = .92f;` — How much of the high end a shut car cuts from its radio for someone outside (`VehicleAudio#shut`). The filter now also runs while the camera is outside any cabin as long as a shut radio plays (`shutRadio`); both effects multiply on a channel.
- `public static boolean listensInside(EntityVehicleF_Physics vehicle)` — Whether the local player rides this car, so its radio plays clear for them whatever the doors do.
- `private static void attach(int source, boolean muffled, float gain, float highs)` — The filter is made on first use and again after the sound engine restarts with a new OpenAL context (resource reload). Without the EFX extension nothing is filtered.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/SoundManagerAccessor.java

- `interface SoundManagerAccessor` — The sound engine behind the sound manager, for `CabinAcoustics`.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/SoundEngineAccessor.java

- `interface SoundEngineAccessor` — The playing sounds and their channels, and whether the engine is loaded, for `CabinAcoustics`.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/ChannelAccessor.java

- `interface ChannelAccessor` — The OpenAL source of a channel, which the cabin filter is attached to.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/LocalPlayerMixin.java

- `private void slimvehicle$noSneakingInSeat(CallbackInfoReturnable<Boolean> cir)` — Shift does nothing in a seat: the player does not crouch (the eye would drop by 0.35 and the body bend), and no sneak reaches the server, whose `PlayerMixin` would refuse the dismount anyway. Leaving a seat sends the release once, so Shift held while boarding does not stick.
- `private void slimvehicle$keepItemsInSeat(boolean all, CallbackInfoReturnable<Boolean> cir)` — Q in a seat drops nothing. The server already ignores the drop (`ServerGamePacketListenerImplMixin`), but the client took the item out of the hotbar first, so it looked thrown until the next inventory sync.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/ClientPacketListenerMixin.java

- `class ClientPacketListenerMixin` — No "Press Shift to dismount" over the hotbar (nor its narration) when the player boards a mod vehicle: Shift does not leave a car (`PlayerMixin`); the exit key is on the dashboard hints.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/effects/VehicleEffects.java

### VehicleEffects

- `private enum Surface` — Imprint tint per surface. Tracks multiply the ground they lie on (like the vanilla block-breaking overlay), so the block texture and its lighting show through; 1 leaves the ground unchanged.
- `private static final class Engine` — Per-vehicle state: the start whose sound was played (`cued`) and whose catch has smoked (`caught`), and last speed and rpm for the load.
- `Map<String, Integer> RETRIES` — When each loop was last (re)started. A loop the sound engine no longer plays (a resource reload restarts the engine and drops every channel, while our instance is never told to stop) is started again, at most once a second. Before, such a loop counted as playing forever and the car stayed silent.
- `private static final class Fire` — Per-vehicle fire: when the flames took hold (they spread from the engine bay over the vehicle) and when it crackles next.
- `ResourceLocation TRACKS = SlimVehicle.id("textures/effect/tyre_tracks.png")` — Pixel-art masks at block-texture resolution (16 texels per block): tread imprint on the left half, skid rubber on the right half; one block per tile along the track.
- `private static final double TRACK_HALF_WIDTH = 5 / 32.0;` — Five texels of the 16-per-block grid, as wide as the tyre.
- `public static final int TRACK_LIFETIME = 100;` — Tracks stay for five seconds and fade out over the last two and a half (TRACK_FADE).
- `private static float density(Minecraft mc)` — Share of effect particles the video settings allow.
- `private static int count(RandomSource random, float count)` — Spawns on average count particles per call: whole ones always, the fraction by chance.
- `private static void exhaust(Minecraft mc, EntityVehicleF_Physics vehicle)` — Tailpipe smoke tied to what the engine does and sounds like: nothing while the starter turns, a dark cloud and a flame when it catches (the start sound's `start_catch`), heavy smoke that thins out over `start_seconds`, then a thin idle haze growing with load and with the revs climbing, and flames at the pops the server announces (`POP_SEQUENCE`) on the bangs of the pop sound (`pop_ticks`).
- `private static void start(Minecraft mc, EntityVehicleF_Physics vehicle)` — Plays the start sound of a start this client witnessed (`startedAt`), once: the cabin recording when one sits in the closed cabin, else the outside one at the engine bay (muffled inside).
- `private static void engineSmoke(Minecraft mc, EntityVehicleF_Physics vehicle)` — A damaged engine smokes from under the bonnet: light wisps below half condition, a steady grey plume below a quarter. Once it burns, the fire makes its own smoke.
- `private static void fire(Minecraft mc, EntityVehicleF_Physics vehicle)` — A burning vehicle. Flames lick out of the engine bay while the engine burns; a wrecked vehicle catches fire all over, the flames spreading from the engine bay to the other spots of the definition over a few seconds. Each burning spot throws flickering tongues of flame, sparks carried up by the heat and a column of black smoke that drifts with the wind; the fire crackles and lights the area around it (see `VehicleLights`).
- `private static void burn(Minecraft mc, EntityVehicleF_Physics vehicle, JSONVehicle.Emitter spot, float strength, Point3D motion, Point3D wind, float density, float smoke)` — One burning spot at strength 0..1: flames, sparks and smoke from a box around the emitter. smoke is this spot's share of the vehicle's smoke; fewer puffs are made a little denser.
- `private static Point3D wind(Minecraft mc)` — A light breeze that slowly turns, stronger in rain.
- `private static void leak(Minecraft mc, EntityVehicleF_Physics vehicle)` — Fuel dripping from a crushed tank.
- `private static void loop(Minecraft mc, EntityVehicleF_Physics vehicle, VehicleAudio.Kind kind, int index, String sound, JSONSounds.EngineLayer layer)` — Starts a vehicle loop that is not playing yet. Loops are keyed by vehicle, kind and layer index, so two layers may use the same sound file. Silent names play nothing. The radio comes from `VehicleAudio#radio`, which picks the tuned station of this client's music or the vehicle's own radio sound.
- `private static VertexBuffer mesh;` — One small mesh, rebuilt once per tick (marks and their fade only change per tick) around a fixed origin near the camera and drawn every frame from the GPU in a single call.
- `private static void stamp(Contact from, Contact to, float strength, boolean skid)` — Lays the strip between two consecutive contacts. V follows the travelled distance (one texture tile per block, repeated), so the tread keeps its pixel size at any speed.

### VehicleEffects.exhaust(Minecraft,EntityVehicleF_Physics)

- `for (JSONVehicle.Tailpipe tailpipe : tailpipes) {` — Each pipe smokes and pops on its own, blowing its own way, so a quad exhaust shows four times the smoke of a single one.
- `int interval = load > .7f ? 1 : load > .35f ? 2 : rev > .25f ? 3 : 4;` — 1 puff every 4 ticks at idle, every tick under full load.
- `float load = Mth.clamp(accel * 90 + rev * .45f + Math.max(0, rise) / span * 6, 0, 1);` — Revs climbing make smoke like a car pulling away: a blip of the throttle at a standstill puffs with the roar of the engine sound.
- `float rev = Mth.clamp((rpm - (float) revs.idleRpm) / span, 0, 1);` — Revs as a share of the car's own range; the fixed 850..6800 of the Volga made the V8's idle count as revving.
- `boolean pop = fx.flames && definition.sounds.popTicks.contains(vehicle.ticks() - vehicle.popAt);` — Flames on the bangs of the pop sound the server played for the same event.
- `jet(random, pipe, back, motion, 4, 1);` — A bang is a short jet of round fireballs out of the pipe (`jet`) and one puff of smoke. The old flame tongues (the fire sheet, tall and narrow) stood up from the pipe like a candle and read as strange; a backfire is a ball of flame thrown backwards. `if (fx.startFlame) jet(…, 3, .8f)` is the smaller one when the engine catches.

### VehicleEffects.jet(RandomSource,Point3D,Point3D,Point3D,int,float)

- `Point3D at = pipe.add(back.scale(.03 + i * .09))` — Each ball of the jet starts a little further out, flies a little faster and lives a tick longer than the one before, so they read as one burst leaving the pipe: small and white-hot at the mouth, larger and redder outwards (the `backfire` sprites). `flare` varies the size of a whole burst.
- `boolean cranking = sinceStart < catchTicks;` — `sinceStart` counts client ticks from a start this client saw; for an engine already running it is large and none of the start effects show.

### VehicleEffects.fire(Minecraft,EntityVehicleF_Physics)

- `int spots = 1;` — The spots of a vehicle burning all over feed one cloud of smoke. Each with a column of its own stacked hundreds of large translucent puffs over the car, which cost a lot of frame rate up close.
- `float smoke = 1f / spots;` — Together the spots make as much smoke as one: about 75 puffs in the air per vehicle instead of over 200.
- `float density = density(mc) * Mth.clamp((float) (FIRE_DETAIL * FIRE_DETAIL / Math.max(1, distance)), FIRE_LEAST, 1);` — A vehicle further than FIRE_DETAIL blocks from the camera burns with fewer particles, by the square of the distance, down to FIRE_LEAST. Every car within 48 blocks used to burn at full detail (some 600 particles each, most of them large translucent smoke): a few burning cars slowed the client until it answered the server late, which showed as rising ping.
- `if (distance > CRACKLE_RANGE * CRACKLE_RANGE) return;` — Only fires within CRACKLE_RANGE blocks crackle.
- `burn(mc, vehicle, bay, strength * Mth.clamp(.35f + burning / 60f, 0, 1), motion, wind, density, smoke);` — The engine bay takes a few seconds to burn up to full strength.

### VehicleEffects.burn(Minecraft,EntityVehicleF_Physics,JSONVehicle.Emitter,float,Point3D,Point3D,float,float)

- `if (strength > .4f && random.nextFloat() < strength * density) {` — A hot core low in the fire.

### VehicleEffects.wheels(Minecraft,EntityVehicleF_Physics)

- `boolean driven = !wheel.definition.steer;` — Tyre smoke from the slipping (driven or locked) wheels.

### VehicleEffects.render(WorldRenderContext)

- `RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);` — result = ground * (1 - mask * alpha * (1 - tint)): a darker imprint of whatever block is below.

### VehicleEffects.rebuildTracks(Vec3)

- `float r = mark.surface().r * a;` — Premultiplied: the mask texture stores its alpha in RGB as well.

### VehicleEffects.stamp(Contact,Contact,float,boolean)

- `if (Math.abs(l1.y - l0.y) >= .12 || Math.abs(r1.y - r0.y) >= .12) return;` — A strip across a step would hang in the air or cut into the block.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/effects/VehicleParticles.java

- `class VehicleParticles` — Vehicle particles drawn with the mod's own sprites (see particles/*.json and `tools/GenerateParticleTextures.java`).

### VehicleParticles.Kind

- `EXHAUST(true, false, 0, .955f),` — Lifecycle sequence smoke_0..11: puff grows, then breaks up.
- `FLAME(false, false, 0, .9f),` — A tongue of flame: the fire_0..6 frames flicker in a loop while it rises, swells and burns out from white-yellow through orange to a dull red tip. Lit by itself.
- `EMBER(false, false, 0, .96f),` — A glowing spark carried up by the heat, wobbling and blinking out.
- `FIRE_SMOKE(true, false, 0, .97f);` — Thick smoke over a fire: the smoke_0..11 frames, rising fast and spreading wide.
- `Kind sheet()` — Kind whose sprite sheet this one draws with. `BACKFIRE` has its own round fireballs (backfire_0..4); `FLAME` and `EMBER` draw with the fire tongues (fire_0..6, `particles/flame.json`), which `BACKFIRE` used before.

### VehicleParticles

- `public static void spawn(Kind kind, Point3D at, Point3D velocity, int lifetime, float size0, float size1, float alpha, float shade)` — Spawns a configured particle; does nothing until the sprite atlas is loaded.

### VehicleParticles.Puff

- `private final int frame;` — Loop offset of a flame's frames and phase of an ember's wobble.
- `private void flicker()` — Next frame of the fire loop, two ticks each.
- `private void tint(float life)` — Flame colour along its life: hot white-yellow, orange, then a dull red tip. Shade scales it, and every channel stays at most 1: the vertex colour is stored in a byte without clamping, so the hot core (shade 1.15) wrapped its red to about 37 of 255 and burned green and turquoise, deep in the wheel arches and the engine bay.

### VehicleParticles.Puff.tick()

- `yd += .0035;` — Hot gas speeds up as it rises and sways a little; the tip fades as it burns out.
- `roll += .008f;` — Fades in out of the flames, drifts with the wind and thins out high up.
- `alpha = startAlpha * Mth.clamp((1 - life) / .45f, 0, 1);` — Fade out over the last 45 % of the lifetime; smoke slowly turns as it drifts.

### VehicleParticles.Puff.getQuadSize(float)

- `if (kind == Kind.FLAME) return t < .25f ? Mth.lerp(t / .25f, size0, size1) : Mth.lerp((t - .25f) / .75f, size1, size1 * .3f);` — A flame swells quickly to full size, then narrows towards its tip.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/hud/VehicleHud.java

- `class VehicleHud` — Dashboard while seated, in pixel art at one art pixel per GUI unit: the speedometer and tachometer on the customer's dial (`DialFace`), between them a framed module with the turn signals, headlights, the gear, fuel and body bars and a row of warning lamps; above it a contextual hint, beside it the key hints, and the damage diagram (`DamageDiagram`) once the car is hurt. On foot and seated, the part a right click will use. Strings come from the lang files.
- `private static final int WIDTH = DIAL + GAP + MODULE_WIDTH + GAP + DIAL;` — 348×132 at scale 1: two 128-pixel dials and the 84×104 module, bottom-aligned.
- `private static final int READOUT = 91;` — Digital readouts sit low in each dial (digits from `READOUT`, the unit at `UNIT`), in the opening of the scale between its first and last label. The needle never sweeps the ±42° around straight down that this opening spans (`DialFace#MAJOR` ends at ±137.6°), and the four-digit box with its corners at about 40° stays inside it, so no needle position crosses a digit. At 76 a needle near zero ran across the leading digits.
- `private static final int KEY_FACE = 0xff913432;` — Key caps in the red asked for: face #913432, a lit top-left edge, a dark bottom-right one.
- `private static final int[] SPEED_STEPS = {5, 10, 15, 20, 25, 30, 40, 50, 60, 80, 100};` — The dial has eight labels, seven intervals: the speedometer counts in the smallest of these steps that reaches the definition's `render.speedometer` in seven (the G 63's 280 gives 0, 40 … 280).
- `private static final int RPM_STEP = 50;` — The tachometer readout moves in 50 rpm steps, so idle does not flicker in the last digits.

### VehicleHud.render(GuiGraphics,float)

- `float blend = vehicle == lastVehicle ? 1 - (float) Math.exp(-(now - lastFrame) / 70.0) : 1;` — Needles and readouts glide between 20 Hz updates.
- `gui.drawManaged(() -> {` — Fills and strings are batched until the geometry type changes. The dial faces and icons are blits, which draw at once, and the tinted icons flush the batch (`setColor`), so the dashboard draws the faces first, then the fills and text, and the icons last.

### VehicleHud

- `private static void prompt(GuiGraphics gui, Font font, int centre, int bottom)` — The part a right click will use (a door, the bonnet, a seat, the glovebox) at the bottom centre: a red key cap with the player's own use key (`VehicleControls#useKeyName`) and the action, styled like the key hints. On foot it sits above the hotbar, and in survival above the action bar (`ABOVE_ACTION_BAR`), so messages never cover it; seated above the dashboard or the passenger's speed. Wheels get no prompt: a click on one does nothing, and their zones are there for aiming and damage only.
- `private static int hints(GuiGraphics gui, Font font, List<Hint> hints, int edge, int top, boolean leftSide)` — A column of key hints aligned like the reference: on the left the caps are flush against the dashboard and the labels end right before the widest caps; on the right the caps start at the dashboard and the labels after the widest caps. Rows are `HINT_ROW` apart and the column is centred on the dials. A label is white while that thing is on (the horn while held). Returns the column's width, which moves the damage diagram out past it. `hints=false` in `slimvehicle-client.properties` hides them.
- `private static List<Hint> driverLeft(EntityVehicleF_Physics vehicle)` — Driving keys on the left (engine, lights, gears or selector as one hint with both caps, handbrake, horn, exit), the car's equipment on the right (`driverRight`: lock, window, wipers and siren where the car has them, radio, vibration). A passenger gets window and exit beside the hotbar. While the radio plays music (`RadioStations#any`) a "Station" hint with the previous and next caps follows the radio hint.
- `private static void segments(GuiGraphics gui, Font font, List<Segment> segments, int centre, int top)` — One centred line of key caps and text: the contextual hint and the prompt.
- `private static List<Segment> hint(EntityVehicleF_Physics vehicle)` — What to do next, with the key to do it: start the engine, or (no key in the inventory) that it will not start; at a standstill in neutral, which keys drive off or reverse. Wrecked, dead and empty need no key and say so in plain text.
- `private static void readout(GuiGraphics gui, Font font, int value, int count, boolean lit, int centre, int y)` — Digits at double size with leading zeros dimmed, like an LCD. The tachometer's are all dim while the engine is off; that, the needle at rest and the sound replace the "engine on/off" words it used to print, which ran over the scale.
- `private static void needle(GuiGraphics gui, int cx, int cy, float angle)` — A 2-pixel needle with a darker shadow column, over a hub disc.
- `private static void icons(GuiGraphics gui, EntityVehicleF_Physics vehicle, int x, int y, boolean blink)` — Icons from `textures/gui/hud_icons.png` (`tools/GenerateHudTextures.java`), white art tinted per state: turn arrows green when blinking, headlights blue, then the lamp row (handbrake, lock, siren, radio, wipers, check engine, and a blinking key while no key to this car is in the inventory).
- `private static void bar(GuiGraphics gui, int x, int y, float level, int color)` — Seven-segment bars for fuel and body condition.

### VehicleHud.dashboard(GuiGraphics,Font,EntityVehicleF_Physics)

- `float scale = Mth.clamp(Math.min(gui.guiWidth() / 600f, gui.guiHeight() / 330f), .45f, 1f);` — Fit small windows too; at the usual GUI scales it is 1, so art pixels stay whole.
- `int rpmStep = Math.max(1, (int) Math.ceil(Math.ceil(definition.engine.maxRpm / 1000) / (DialFace.MAJOR.length - 1)));` — Tachometer labels in thousands, red from the red line.
- `if (DamageDiagram.shown(vehicle)) {` — The damage diagram in a framed panel like the module, left of the left hint column, centred on the dials.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/hud/DialFace.java

- `final class DialFace` — The face of a dial, built at runtime from `textures/gui/dial.png` and `dial_digits.png` and cached as a texture per scale. dial.png is the customer's Sprite-0002 (a 128×128 pixel-art speedometer) with its printed numbers removed, so one ring serves every scale. The face is filled dark (`FACE`), ticks past the red line turn red, a red arc runs along the rim from there, and the labels are stamped in the customer's 4×6 digit style.
- `static final float[] MAJOR = {-137.6f, -99.4f, -60.3f, -27f, 27f, 60.3f, 99.4f, 137.6f};` — Angles (degrees from straight up, clockwise) of the eight long ticks of the drawn ring. They are not evenly spaced: the art leaves a gap at the top. Values between labels are interpolated linearly per interval (`angle`), so a needle always points at the printed number.
- `static float angle(float value, float step)` — Needle angle for a value on a scale with that step; it may run 6 % of an interval past the last label, like a real gauge pinned on its stop.
- `static ResourceLocation face(int step, float redFrom)` — `redFrom` in label units, below 0 for no red zone. Null when the textures could not be read; the dashboard then draws without the face.
- `static void clear()` — Releases the built faces; `VehicleHud#registerReload` calls it on a resource reload, so an edited ring or digit sheet shows without a restart.
- `private static int argb(int abgr)` — `NativeImage` pixels are ABGR; the colours here are written as ARGB.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/hud/DamageDiagram.java

- `final class DamageDiagram` — The car seen from above, nose up, drawn pixel by pixel with fills (no texture): every body panel tinted by the damage of its zone, glass, mirrors, lamps and tyres. The old diagram was a stack of plain rectangles. Built once per definition (`build`) from the damage block's half width and length, so a narrow car is drawn narrow, and redrawn each frame from the synced damage (runs of one colour per row become one fill).
- `private static final int LENGTH = 60;` — Body length in pixels; the width follows the definition (16..30, even, so the car is symmetric), plus `MARGIN` around it for the outline, mirrors and tyres.
- `private static int damage(float level, boolean blink)` — Undamaged panels are steel grey; any damage turns a panel yellow, through amber to red at full, and a destroyed one blinks red.
- `private static final Map<String, Layout> LAYOUTS = new HashMap<>();` — By vehicle id, rebuilt when the definition object changes (joining a server rebuilds the definitions with its configs), so no stale layouts pile up.
- `private static Layout build(JSONVehicle definition)` — Regions in rows from the nose: bumper (front zone), bonnet (front), windshield (glass of the front zone), roof (top zone), rear window (glass of the rear zone), boot and rear bumper (rear zone). A strip down each side is that side's zones in order along the car (wing, doors, quarter), chosen like the damage model picks a zone along a side (`along`, the nearest `from`..`to`). A dark one-pixel seam runs wherever two zones meet, on the inner pixel, so the panels read separately and symmetrically.
- `boolean taper = j == hood || j == trunk - 1;` — The windshield and rear window are one pixel narrower at their outer edge, which reads as pillars.
- `shades[at] = (byte) (j < 2 || j >= LENGTH - 2 ? 90 : side ? 86 : j >= screen && j < rearGlass ? 108 : 100);` — A little shading for depth: sides and bumpers darker, the roof lighter; damage colours are shaded the same way.
- `for (AJSONMultipart.Wheel wheel : definition.wheels) {` — Tyres stand one pixel out of the body at each wheel's position and size and turn blinking red when punctured. Headlights and tail lamps are the corner pixels of the bumpers: lit with the lights, dark when broken. Broken glass shows as a chequered crack pattern.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/hud/InteractionOverlay.java

### InteractionOverlay.tick(Minecraft)

- `if (mc.player == null || mc.level == null || mc.screen != null || TaczCompat.holdsGun(mc.player)) return;` — With a TaCZ gun in hand the use key aims, so there is no use-key prompt and no target; the interact (`U`) and enter (`F`) keys still find the car under the crosshair (`VehicleControls#targetAction`).
- `if (ridden != null) {` — There is no outside interaction target while seated (only the glovebox from the front seats). Skipping the per-tick vehicle ray scan removes needless SAT tests during driving.

### InteractionOverlay

- `public static void draw(PoseStack pose, VertexConsumer buffer, Hull.Box box, Point3D origin, int color, float alpha)` — Outline of an oriented box, for `F3+B` (`EntityRenderDispatcherMixin`).

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/common/EntityMixin.java

- `class EntityMixin` — The camera reads getEyeHeight(), but getEyeY()/getEyePosition() read the standing eye-height field. Both must agree: interaction rays from a seat used to start 1.62 above the feet, above the roof, so the roof hid the glovebox and every other cabin control.
- `private float slimvehicle$seatedEyes()` — Eye height of a player sitting in a vehicle seat, or NaN if the player is not seated.
- `private Object slimvehicle$wrapper;` — The entity's `WrapperEntity` (`IWrapperHolder`), made on first use.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/common/EntityGetterMixin.java

- `interface EntityGetterMixin` — Entity collisions near vehicles: a vehicle gives its own shape for the entity that moves (`AEntityE_Interactable#collisionBoxes`), any other entity its box. Used for movement on both sides and by the server's check of a player's move.
- `if (BuilderVehicle.ignoringVehicles()) continue;` — Vehicles are left out while vanilla checks whether a player fits a pose (`PlayerMixin`); movement still collides with them.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/common/PlayerMixin.java

- `private boolean slimvehicle$vehiclesNeverForcePose(Level level, Entity entity, AABB box, Operation<Boolean> original)` — Vanilla picks a player's pose each tick from what fits: standing, else crouching, else crawling (`Player#updatePlayerPose`), and the car's boxes count as entity collisions there. An open door is a solid overhang beside the car, so a player under one (the door swung open onto them, or they walked under it) could neither stand nor crouch and dropped into the crawl, lying flat by the door; the crawl box is too low to touch the door, so `pushOut` never freed them either. In that check vehicles now count for nothing: the player stays upright, and `pushOut` moves them out of the door at once.
- `private void slimvehicle$shiftKeepsTheSeat(CallbackInfoReturnable<Boolean> cir)` — Vanilla dismounts a rider on the server whenever their sneak flag is set (`Player#rideTick`). A car is left only with the exit key (`VehicleAction#EXIT`), which opens the door and puts the rider beside it; Shift used to drop them out through the closed door, and holding Shift while boarding with Shift+right click threw them straight back out. The client no longer sneaks in a seat either (`LocalPlayerMixin`).
- `private void slimvehicle$noPickupFromSeat(Entity entity, CallbackInfo ci)` — A rider picks up items and arrows from the vehicle's whole box widened by a block (`Player#aiStep`), so a driver collected everything around the car. Seated players pick nothing up; experience orbs still come.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/common/AbstractContainerMenuMixin.java

- `private void slimvehicle$noThrowingFromSeat(int slot, int button, ClickType type, Player player, CallbackInfo ci)` — Seated players throw nothing out of an inventory window (trunk, glovebox): neither Q over a slot (`THROW`) nor a click outside the window with an item on the cursor. A click at `SLOT_CLICKED_OUTSIDE` as part of a drag (`QUICK_CRAFT`) is not a throw and still spreads the stack. Runs on both sides, so the client's predicted click matches the server's.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/common/ProjectileUtilMixin.java

- `class ProjectileUtilMixin` — Picking and vanilla projectile impacts use the model surface, never the broad-phase box.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/common/ServerGamePacketListenerImplMixin.java

- `class ServerGamePacketListenerImplMixin` — The server owns vehicle motion: client vehicle-move packets are dropped, and riders cannot drop, swap or mine. The client does not try to drop either (`LocalPlayerMixin`), and windows do not throw (`AbstractContainerMenuMixin`).

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/compat/EntityKineticBulletMixin.java

- `class EntityKineticBulletMixin` — TaCZ bullets (optional mod, `@Pseudo`): the bullet's own trajectory hits a tyre, pane or passenger through `AEntityVehicleC_Colliding#projectileHit` instead of the vehicle's bounding box.
- `if (bullet.getOwner() != null && bullet.getOwner().getRootVehicle() == vehicle) {` — The shooter's own car, found on the way out by `EntityUtilMixin`: the shot ends at the pane or panel it meets from inside (`cabinShot`).

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/compat/EntityUtilMixin.java

- `class EntityUtilMixin` — TaCZ's search for what a bullet hits skips the shooter's vehicle (and its riders) entirely, so a passenger shot straight through closed glass and doors. The shooter's car now joins the search with the first thing the bullet meets on its way out of the cabin (`AEntityVehicleC_Colliding#cabinHit`); an open, lowered or broken window lets it through, so shooting from a car needs the window down (or breaks it). Riders are still skipped, as TaCZ does.
- `private static void slimvehicle$ownCabinPierced(Projectile bullet, Vec3 start, Vec3 end, CallbackInfoReturnable<List<EntityKineticBullet.EntityResult>> cir)` — The same for piercing rounds; TaCZ sorts the list by distance before it hits them in turn.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/compat/InputExtraCheckMixin.java

- `class InputExtraCheckMixin` — TaCZ asks `InputExtraCheck#isInGame` before every key it handles: shooting (and the attack key it shoots with), aiming, reloading, inspecting, fire mode, melee, zoom, attachments, crawling, its interact key and its config key. For the driver of a car it answers false, so TaCZ ignores the driver entirely and its R, H, G, V and Z no longer double the car's engine, lights, horn, siren and gear keys.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/compat/MosvegasCompat.java

- `class MosvegasCompat` — Zone protection of Mosvegas Core (`mosvegas_core`, optional), the core mod of the city server the cars are made for. Without it every check allows, as before. It touches no class of the core: only `MosvegasZones` does, and it is loaded only when the mod is, like `TaczCompat` and `TaczEvents`.
- `return MosvegasZones.allows(server, player instanceof ServerPlayer serverPlayer ? serverPlayer : null, access, vehicle);` — The core's API takes server players. Anyone else (a mock player of the vehicle game tests, a mob) goes in as no player, whom a `members` rule never lets in.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/compat/MosvegasZones.java

- `final class MosvegasZones` — The core's public API (`ru.mosvegas.core.api.TransportAccess`, `ZoneAccess`, API version 1, documented in the core's `transtortintegration.md`). Rules: `vehicle_enter`, `vehicle_use`, `vehicle_place`, `containers`, `vehicle_damage`, `terrain`. Every check covers the vehicle's whole bounding box, so a car half in a zone is in it. The core also closes private apartments and parkings to placing (`PropertyService`) and lets operators (level 3) and `mosvegas.zone.bypass.<rule>` through: an operator never sees these refusals.
- `case PLACE -> TransportAccess.canPlace(player, level, vehicle.getBoundingBox());` — The new car is not in the level yet, so the box of the positioned entity is passed with the level.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/compat/TaczCompat.java

- `class TaczCompat` — Timeless and Classics Guns (TaCZ, optional). Only `TaczEvents` and the client's `TaczClientCompat` touch TaCZ classes, and only when the mod is loaded, so Slim Vehicle runs without it.
- `public static boolean driving(Entity entity)` — Whether the entity is a player driving a vehicle: the one rider TaCZ is switched off for.
- `public static boolean holdsGun(Player player)` — A TaCZ gun in the main hand. The use key then aims, so it must not click doors from a seat (`MinecraftMixin`) or show the use-key prompt (`InteractionOverlay`).

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/compat/TaczEvents.java

- `static void register()` — The server's side of switching TaCZ off for drivers: shots, reloads, melee and fire mode changes of a driver are cancelled, so a client without the input check (`InputExtraCheckMixin`) cannot shoot from the wheel either. The events fire on both sides; the client's own cancel stops the shot's sound and animation too.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/client/TaczClientCompat.java

- `static void tick(Minecraft mc)` — A player who was aiming when they got behind the wheel stays zoomed in, since TaCZ no longer hears the release of the aim key (`InputExtraCheckMixin`); the aim is dropped every tick while driving.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/MinecraftMixin.java

- `private void slimvehicle$useOwnVehicle(CallbackInfo ci)` — The use key from a seat clicks the car's own parts (`VehicleControls#interactSeated`) instead of vanilla's item use. With a TaCZ gun in hand it does nothing: the key aims then, and holding it to aim used to open and close the door every few ticks (TaCZ blocks vanilla's use with a gun only further down in `startUseItem`, after this hook).

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/CameraMixin.java

- `class CameraMixin` — Seats are low, so a third-person camera swung down used to end up just inside the road surface. From there vanilla cannot cull the world below and renders every cave in view: a huge FPS drop. Keep the camera a little in front of the first block along the view ray. In first person the driver's eye turns with the head on its neck (`DriverPose#eye`).

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/EntityMixin.java

- `class EntityMixin` — Mouse look is applied every frame through Entity.turn; clamp the first-person driver right there.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/EntityRenderDispatcherMixin.java

- `private static void slimvehicle$vehicleContours(PoseStack pose, VertexConsumer buffer, Entity entity, float partial, CallbackInfo ci)` — `F3+B` shows the hull and the click zones instead of the broad-phase box.
- `private boolean slimvehicle$noRiderShadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float strength, float partial, LevelReader level, float radius)` — Riders are inside the body: the vehicle casts the shadow, not a floating disc under each seat.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/PlayerRendererMixin.java

- `class PlayerRendererMixin` — Riders of a seat (not standing ones) are drawn at the vehicle's rider scale and lean with the body.

## sliminterfacefabric/src/main/java/studio/merami/sliminterfacefabric/mixin/client/PackRepositoryMixin.java

- `class PackRepositoryMixin` — Adds `FolderPackSource` to the client's resource pack repository only (the one built with `ClientPackSource`); the server reads pack definitions directly and needs no data pack.

## sliminterfacefabric/src/gametest/java/studio/merami/sliminterfacefabric/gametest/GameTestSupport.java

- `class GameTestSupport` — Shared setup of the game tests. Tests drive the core vehicle (`EntityVehicleF_Physics`) and vanilla players; `p`/`v`, `box`/`aabb` and `w` convert between them, `entity` is the vehicle's `BuilderVehicle` for what only the game entity does (adding to the level, loading NBT, its tick count).
- `static void floor(GameTestHelper h, int x0, int x1, int z0, int z1)` — Stone floor over [x0,x1)×[z0,z1) with three blocks of air above it.
- `static void own(EntityVehicleF_Physics car, Player player)` — Makes a player the owner and gives them the car's key, as placing a car does. Owning no longer counts as holding the key (`hasKey`), so tests that start or lock a car need both.
- `static EntityVehicleF_Physics car(GameTestHelper h, double x, double z, float yaw, String definition)` — A car of the given definition (the default car for null) resting on the floor at (x, z), not added to the level: the test ticks it.
- `static void fall(EntityVehicleF_Physics car, Player player, Vec3 input, boolean jump)` — `walk` with gravity, jumping and vanilla's air drag (0.91 off the ground), for players on top of a car or thrown by one.
- `static void noseTo(EntityVehicleF_Physics car, double z, double gap)` — Moves a yaw-0 car along Z so its nose is gap blocks short of the plane z (absolute).
- `static Point3D worldDirection(EntityVehicleF_Physics car, Point3D local)` — A chassis-space direction of a car in world axes.
- `static void open(EntityVehicleF_Physics car, PartOpening part)` — Opens a part at once, without animation, and rebuilds the hull with it open.
- `static void lookAt(Player player, Point3D target)` — Turns a player's view to a point.
- `static void walk(EntityVehicleF_Physics car, Player player, Vec3 input)` — One client tick of a player walking: input, move, ground friction, then the push out of the car (`VehicleControls`).
- `static void stroll(List<EntityVehicleF_Physics> cars, Player player, Vec3 input)` — `walk` for several cars.
- `static double distance(Hull.Box box, Point3D point)` — Distance from a point to a box (0 inside it).
- `static Hull.Box openDoorBox(EntityVehicleF_Physics car)` — The left front door's collision box.
- `static Hull.Box control(EntityVehicleF_Physics car, String opening)` — The click box of an opening.
- `static void assertWheelsAttached(GameTestHelper h, EntityVehicleF_Physics car, String where)` — Wheels may compress into the arch but never visibly hang below the body.
- `static void assertBodyAboveGround(GameTestHelper h, EntityVehicleF_Physics car, String where)` — The lowest body panel stays above the block under it.

## sliminterfacefabric/src/gametest/java/studio/merami/sliminterfacefabric/gametest/DrivingGameTests.java

### DrivingGameTests.automaticEngagesDriveAndShiftsByItself(GameTestHelper)

- `step(car);` — The level advances `tickCount` before it ticks an entity; a test that ticks the car itself must do it too (`GameTestSupport#step`), or every cooldown counted in ticks (the gearbox's pause between shifts) never runs out.
- `place(player, car.position());` — The mock rider is not moved with the car when the test ticks the car directly, and commands from more than 8 blocks away are ignored.

### DrivingGameTests.climbsAtAnglesAndAcrossContactSeams(GameTestHelper)

- `if (tick % 3 == 0) car.setOnGround(false);` — Reproduce the intermittent loss of the vanilla ground flag.
- `if ((car.z() - start) * (reverse ? -1 : 1) < 11) largestJump = Math.max(largestJump, Math.abs(body - oldBody));` — Measure the curb crossing only; the platform ends ~21 blocks ahead and the car drops off it.
- `h.assertTrue(largestJump < .2, "Collision steps must not jerk the rendered body: " + largestJump);` — A half-block climb completes within about three ticks; the body may not lag behind (it would sink).

### DrivingGameTests.turnsSmoothlyAcrossPathEdges(GameTestHelper)

- `h.setBlock(x, 0, z, x % 3 == 0 && z % 3 == 0 ? Blocks.GRASS_BLOCK : Blocks.DIRT_PATH);` — Dirt path is 1/16 lower than grass: corners swinging over a stripe used to cancel the turn for a tick.
- `Vec3 start = h.absoluteVec(new Vec3(12.5, 2, 20.5));` — A left turn from here circles towards +X and stays on the 40x40 course.

### DrivingGameTests.movingCarSendsAPoseEveryTickAndAParkedOneNone(GameTestHelper)

- `step(car);` — The test ticks the car itself, so it also advances its tick count like the level does.

## sliminterfacefabric/src/gametest/java/studio/merami/sliminterfacefabric/gametest/CollisionGameTests.java

### CollisionGameTests.modelContours(GameTestHelper)

- `Point3D air = car.localToWorld(new Point3D(0, 1.5, 2.55));` — Air above the bonnet lies within the broad-phase entity bounds, but is empty.

### CollisionGameTests.punctureLeansHullWithModel(GameTestHelper)

- `ChassisGeometry.Pose lean = car.puncturePose();` — The body panel over the flat tyre drops by the same amount as the rendered chassis.

### CollisionGameTests.playerCaughtInsideCarIsReleasedAndPushedOut(GameTestHelper)

- `double ground = h.absoluteVec(new Vec3(0, 1, 0)).y;` — Players stand on the floor. The car rests about a centimetre lower (its tyre contact); feet that deep in the floor stop at the next floor block.
- `for (int tick = 0; tick < 30 && car.hull().intersects(box(player.getBoundingBox().deflate(.02))); tick++) {` — The client's part of the escape: push, move (the car's collision lets go while the player is inside), friction. Out of the body, the last bit is the push out of the door.
- `Hull.Box door = control(car, "door_fl");` — A player standing in an open door, beside the car rather than inside it, is pushed out of it, away from the car.

### CollisionGameTests.walksAlongTheSideOfACar(GameTestHelper)

- `int yaw = new int[]{0, 90, 7, 30}[run % 4];` — Square to the world the protruding tyres and door edges used to stop the player after a third of a block; turned cars hid it. The Volga and the G 63.

### CollisionGameTests.spawnedCarStandsClearOfThePlayer(GameTestHelper)

- `car.moveTo(at.x, ground, at.z + 1.5, player.getYRot(), 0);` — The spawn item puts the car's centre on the clicked block, here just in front of the player.

### CollisionGameTests.playerNeverGetsStuckAroundCars(GameTestHelper)

- `for (double x = 4; x <= 20; x += .4) {` — Every free spot within a block of a car (closed doors, open doors, two cars side by side at slightly different angles): wander there for 25 ticks, then the player must get away in one of eight directions and never end up inside a car. Three scenes with Volgas, three with G 63s.

### CollisionGameTests.playerWalkingOnTheGroundNeverGetsStuck(GameTestHelper)

- `for (int scene = 0; scene < 6; scene++) {` — As `playerNeverGetsStuckAroundCars`, but with gravity, jumps and running: the walkers fall, step up onto car parts and jump onto them (they stand on the car for hundreds of ticks), around G 63s and Volgas with doors shut and open, and around a G 63 with one side on kerb slabs. From every spot the player must get away in one of eight directions and never end up inside the car.

### CollisionGameTests.carDrivenIntoPlayerNeverSwallowsThem(GameTestHelper)

- `h.assertTrue(fastest > .3 || !model.equals("g63"), "Test setup: the " + model + " reaches more than the escape speed: " + fastest * 72 + " km/h");` — Only a car faster than `AEntityE_Interactable` ESCAPE could catch the player inside it; the Volga in first gear never gets there and checks the slow push.

### CollisionGameTests.playerOnTopCanAlwaysGetDown(GameTestHelper)

- `for (int tick = 0; tick < 80; tick++) fall(car, walker, way, tick % 10 == 0);` — Dropped onto every spot of the car, the player walks and jumps in one of eight directions until they stand on the ground: nowhere on the roof, bonnet or tailgate holds them.

### CollisionGameTests.playerUnderAnOpenDoorStaysUpright(GameTestHelper)

- `h.assertFalse(h.getLevel().noCollision(player, standing) || h.getLevel().noCollision(player, crouching),` — The player stands under the G 63's open front door, which hangs into both the standing and the crouching box: before `PlayerMixin` vanilla's pose update made them crawl. A few ticks of the player must leave them standing.

### CollisionGameTests.doorClosedOnPlayerNeverLetsThemIntoTheCabin(GameTestHelper)

- `Point3D start = car.localToWorld(new Point3D(side + player.getBbWidth() / 2 / 1.08 - .1, 0, .4));` — Beside the car with the closed door 0.1 into the player's box, as after it closed on them. The previous fix let go of the door and the player walked through it into the cabin.
- `for (int yaw = 0; yaw < 90; yaw++) {` — Turn the car until the open door lies at 45 degrees to the world axes, where slices stick out of it the most.

### CollisionGameTests.glancingWallContactSlidesInsteadOfSticking(GameTestHelper)

- `EntityVehicleF_Physics car = car(h, 12.5, 5, -12);` — Heading 12 degrees towards the wall on +X.
- `h.assertTrue(car.z() - start > 14, "A glancing wall contact must not stop the car: travel=" + (car.z() - start));` — Stopping dead at the first touch (as before 0.1.8) leaves it about 9 blocks down the course.

### CollisionGameTests.sideContactWithPillarKeepsTheCarMoving(GameTestHelper)

- `double side = car.hull().bounds().maxX - car.x();` — Left side 3 cm from a pillar beside the rear wheel.

### CollisionGameTests.steersAwayFromWallItTouches(GameTestHelper)

- `for (int tick = 0; tick < 80; tick++) {` — Steering right swings the rear corner into the wall on the left. That used to refuse the turn on most ticks until the car had crept away from the wall.

### CollisionGameTests.movingCarShovesParkedCar(GameTestHelper)

- `List<ChunkPos> forced = new ArrayList<>();` — The test area is far out and only partly entity-ticking: a car standing in (or shoved into) a chunk that is not ticked stops dead. Force the whole floor and shove only once both cars tick.
- `boolean ticking = forced.stream().allMatch(c -> h.getLevel().isPositionEntityTicking(c.getWorldPosition()));` — Forced chunks become entity-ticking a few ticks later.

## sliminterfacefabric/src/gametest/java/studio/merami/sliminterfacefabric/gametest/DamageGameTests.java

### DamageGameTests.shotsFromTheCabinStopAtClosedGlassAndDoors(GameTestHelper)

- `Point3D lowLeft = beyond(eye, glassBox(car, left).center.subtract(0, .6, 0));` — Below the window: the door panel, whose box also covers the window, must stop this shot but not the one through the window.
- `car.toggleWindow(1);` — The front passenger's window, still whole, goes down; through its opening the driver's shot must leave the car without breaking anything.

### DamageGameTests.headOnCrashDamagesAndGentleBumpDoesNot(GameTestHelper)

- `fast.push(new Point3D(0, 0, .7), 0);` — About 50 km/h with a rider aboard (no parking brake), and a parked car nudged at 7 km/h.

### DamageGameTests.sideImpactDentsAndJamsOnlyTheDoorItHits(GameTestHelper)

- `car.crash(door, null, .55, worldDirection(car, new Point3D(-1, 0, 0)));` — About 40 km/h into the left front door.
- `car.crash(car.localToWorld(new Point3D(.97, .8, .3)), null, .4, worldDirection(car, new Point3D(-1, 0, 0)));` — A second hit next to it deepens the same dent instead of adding another.
- `car.crash(car.localToWorld(new Point3D(0, .6, -3.05)), null, .13, worldDirection(car, new Point3D(0, 0, 1)));` — A bump at walking pace leaves no mark.

### DamageGameTests.bentFrontWingPullsTheCarToItsSide(GameTestHelper)

- `bent.crash(bent.localToWorld(new Point3D(.99, .7, 2.9)), null, .9, worldDirection(bent, new Point3D(-1, 0, 0)));` — Ahead of the wheel arch, so the tyre stays whole.
- `h.assertTrue(bent.yaw() < -1.5, "Without steering the damaged car drifts to the left: yaw=" + bent.yaw());` — Left is towards negative yaw in Minecraft coordinates.

### DamageGameTests.dentedPanelsStayOnTheirHinges(GameTestHelper)

- `double attached = distance(closed, car.localToWorld(pivot));` — How far the undented flap's hinge is from its box; turning about the hinge keeps that distance.
- `car.crash(car.localToWorld(new Point3D(-.97, flap.y, flap.z)), null, .6, worldDirection(car, new Point3D(1, 0, 0)));` — About 43 km/h into the right rear quarter at the fuel flap.
- `h.assertTrue(Math.abs(hinge - attached) < .02,` — Opened, the flap is still that far from the dented hinge.

### DamageGameTests.worldsSavedBeforeTheCoreSplitLoadUnchanged(GameTestHelper)

- `entity(car).load(tag);` — Vehicles saved by 0.2.3 (before the core left Minecraft's classes; `SaveSnapshots`) load through vanilla's entity load into the same state, line by line, and save back to the same NBT, so existing worlds keep their cars, damage, cargo and owners. The undented hull left it turning about the old hinge outside the bent body.

## sliminterfacefabric/src/gametest/java/studio/merami/sliminterfacefabric/gametest/InteractionGameTests.java

### InteractionGameTests.seatedRiderKeepsTheSeatOnShiftAndHandlesNoItems(GameTestHelper)

- `player.rideTick();` — The server's own dismount check with the sneak flag set (`Player#rideTick`): the driver stays seated.
- `player.aiStep();` — Vanilla's pickup pass, which for a rider covers the car's whole box: the apple at the driver's feet stays on the ground.
- `player.setPos(loot.position());` — After `stopRiding` the player stands at the door; back at the apple the same pass picks it up and Q in a window throws again, so the checks above do not pass for some other reason.
- `player.inventoryMenu.clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, 0, ClickType.PICKUP, player);` — A click outside the window with a stick on the cursor keeps the stick.

### InteractionGameTests.seatedRiderClosesOnlyTheirOwnDoor(GameTestHelper)

- `entity(car).tickCount += 5;` — The passenger has no key to the now locked car; five ticks pass the click debounce of `interactPart`.

### InteractionGameTests.driverExitLeavesEngineIdlingThenSwitchesOff(GameTestHelper)

- `int keep = (int) Math.round(car.definition().engine.keepRunning * 20);` — The police car inherits the Volga's two minutes. The engine idles on the spot, a driver who comes back finds it running, each exit starts the count again, and `keep_running` 0 keeps the old exit that switches everything off.

### InteractionGameTests.keyLocksItsCarFromAfar(GameTestHelper)

- `SlimItems.KEY.useInAir(w(stranger), wrapped);` — Whoever holds the key locks and unlocks the car from 20 blocks (the flash tells one from the other); without a key nothing happens, and past `ItemKey#REACH` the key does not reach.

### InteractionGameTests.onlyTheKeyStartsAndLocksAndTheHornSoundsWhileHeld(GameTestHelper)

- `car.setOwner(owner.getUUID());` — An owner without the key: may take the wheel of the unlocked car but neither start nor lock it; with the key (`VehicleSpawner#giveKey`) the engine starts.
- `car.hornInput(w(passenger), false);` — A passenger's input does not touch the horn; the driver's held horn then stops by itself once their input stops (`HORN_RELEASE`), which `succeedWhen` waits for.

### InteractionGameTests.driverOpensGloveboxFromSeat(GameTestHelper)

- `double eye = car.worldToLocal(p(player.getEyePosition())).y;` — Rays start where the camera is: under the roof, not at the standing eye height above it.
- `for (InteractionZone zone : car.interactionZones()) {` — Nothing outside the cabin is offered from the seat: bonnet ahead, wheels and doors to the side. The driver's own door is the exception (`seatedRiderClosesOnlyTheirOwnDoor`).
- `float towards = player.getYRot();` — The glovebox is about 56 degrees to the side and 30 below the eye; the driver can look there (down to 80 degrees at any heading). Only aiming at it selects it: turned towards it but looking past it, or 35 degrees beside it (more than half its width), selects nothing.
- `for (int pitch = -20; pitch <= 25; pitch += 5) {` — Driving, looking ahead, nothing is offered.
- `glovebox.progress = 1;` — Rebuild the hull with the lid open, or its closed box would stand in front of the contents.
- `Vec3 back = v(lid.box().center).subtract(rear.getEyePosition());` — The rear seats are out of reach of the glovebox.

## sliminterfacefabric/src/gametest/java/studio/merami/sliminterfacefabric/gametest/ZoneGameTests.java

- `class ZoneGameTests` — The zone rules on a server with Mosvegas Core loaded, run alone with `.\gradlew.bat runGametest -PvehicleTests -PzoneTests`: boarding, placing, the wall at a zone that forbids driving and backing away from it, a zone closed under a moving car, the glovebox, explosion damage and knocking down a glass pane. Each test has its own batch: zones belong to the level, and tests of one batch run side by side.
- `ServerPlayer player = h.makeMockServerPlayerInLevel();` — A real server player without operator rights; operators pass every zone. Mock players that ride far from the start are moved back to the car before an action (`place`), which wants the player within 8 blocks.
- `floor(h, 1, …` — Floors start at x 1 like in the other tests: x 0 holds the test's structure block, and `succeed` fails without it.
- `zones(state -> state.protectedWorlds.add(…))` — The terrain test does not trust the world's zone file: it makes the overworld protected itself, then allows `terrain` in a zone.

## sliminterfacefabric/src/gametest/java/studio/merami/sliminterfacefabric/gametest/SaveSnapshots.java

- `final class SaveSnapshots` — A damaged, dented, loaded G 63 and a Volga as 0.2.3 saved them (SNBT from `saveWithoutId`) and the state 0.2.3 read back from them (owner, condition, zones, dents, switches, openings, windows, glass, tyres, lamps, fuel, engine, tint, colour, styles and storage contents). Written once by 0.2.3 itself and never regenerated: they are the reference old worlds have to match.

## sliminterfacefabric/src/gametest/java/studio/merami/sliminterfacefabric/gametest/PackGameTests.java

- `private static JSONVehicle define(String name, JsonObject json, List<String> unknown)` — A definition made in the test, baked like `PackParser` does, without registering it.

### PackGameTests.everyVehicleComesFromItsDefinition(GameTestHelper)

- `for (int x = 1; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);` — Climbing on: a scooter has no door to open first.

### PackGameTests.modelsAndAnimationsPlayWithoutGeckoLib(GameTestHelper)

- `for (ModelSkeleton.Bone bone : ModelSkeleton.bones(definition.modelLocation)) …` — Every bone the hull is built from is also in the drawn model, so what collides is what is drawn.
- `animator.update(106, 5, when);` — The G 63's mirrors fold in 0.6 s on `locked` and unfold on `!locked`: start at rest, half way between, hold the last frame, unfold from folded without a jump, and a bone no animation drives any more returns to rest over one tick. The renderer's exact vertices were compared with GeckoLib 4.4.4 once, before it was removed; this test keeps the animation rules from drifting.
- `MoLang.Value wave = MoLang.parse(…` — Degrees in trigonometry, both query spellings, unary minus and functions; an unknown query is refused by name.

### PackGameTests.panesBreakAndOpeningsAnswerFromOutside(GameTestHelper)

- `car.projectileHit(eye, eye.add(pane.center.subtract(eye).scale(2)), new WrapperDamage(player.damageSources().playerAttack(player)), 4);` — Every pane is reachable from outside: a punch at it leaves it whole, a bullet along the same line breaks it.

### PackGameTests.g63SeatsFiveAndBoardsTheMiddleThroughTheRearDoor(GameTestHelper)

- `for (int x = 1; x < 9; x++) for (int z = 0; z < 9; z++) h.setBlock(x, 0, z, Blocks.STONE);` — Starts at x = 1: the test's structure block sits at (0, 0, 0), and replacing it fails the test when it succeeds.

## slimcore/src/test/java/studio/merami/slimcore/TestPacks.java

- `final class TestPacks` — The built-in pack for JUnit, read straight from `slimcore/src/main/resources` (the tests run from the project root) once per run; `define` bakes a changed copy of a built-in definition without registering it, as `PackParser` would.

## slimcore/src/test/java/studio/merami/slimcore/physics/DrivePhysicsTest.java

- `class DrivePhysicsTest` — The drivetrain against real figures: a G 63 does 0-100 km/h in about 4.5 s, 0-200 in about 17 s and tops out at 240 in ninth; braking stays within tyre grip; full lock turns tightly enough for town streets without sliding; the manual cars climb through their gears to 100 km/h. The pure drivetrain and suspension game tests of 0.2.4 moved here.
- `private static Run floorIt(JSONVehicle definition, int seconds)` — Full throttle from rest through the automatic's own shifts (eight ticks apart, as in `AEntityVehicleE_Powered`).
- `private static double brakingDistance(DrivePhysics.Tune tune, double kmh, int gear)` — Stopping distance with the pedal pressed through `DrivePhysics#brakePressure`, as a driver does.

### DrivePhysicsTest.fullLockTurnsTightlyAndHoldsTheLine()

- `assertTrue(radius < widest[run], id + " at " + kmh + " km/h turns on a radius of at most " + widest[run] + " blocks: " + radius);` — Full lock held for two seconds (a 90° corner) at 30, 60 and 90 km/h, the speed kept with the throttle in a gear with headroom: at most 9.5, 13 and 26 blocks. With the real grip of 0.2.7 the G 63 needed 37 blocks at 60 km/h.
- `assertTrue(speed * KMH > kmh * .8 && speed * KMH < kmh + 4, id + " keeps most of its speed through a two-second turn: " + speed * KMH + " of " + kmh);` — Tyre scrub (`SCRUB`) may cost the 74 kW Volga some speed at 90 km/h, not a fifth of it.

### DrivePhysicsTest.keyboardThrottleOrBrakeInATurnDoesNotSlide()

- `assertTrue(worst < 3, id + " holds its line with throttle " + pedals.throttle() + " and brake " + pedals.brake()` — Keyboard driving: W or S held at full while steering to full lock (at the game's steering rate) from 15 to 120 km/h. Before 0.2.8 these slid by 26 to 50 degrees.

### DrivePhysicsTest.revsFollowTheWheelsAndIdleInNeutral()

- `assertTrue(stall > 2000 && stall < 2600, "The automatic's converter holds the engine near its stall speed when pulling away: " + stall);` — The G 63 pulls away at about 2300 rpm (0.4 of 5800).

## slimcore/src/test/java/studio/merami/slimcore/packloading/VehicleConfigTest.java

- `class VehicleConfigTest` — The per-vehicle config in a temporary folder: written with the car's values, an edited mass that survives a change of the mod's defaults while an untouched value follows it, a value that does not fit falling back to the pack, and a client taking the server's values and dropping them on leaving. Reloads the built-in pack without configs at the end, for the tests that follow.

## slimcore/src/test/java/studio/merami/slimcore/systems/RadioLibraryTest.java

- `class RadioLibraryTest` — The radio folder as a server and a client read it: lengths from the Ogg pages equal to what stb_vorbis reports for the mod's own sounds, Opus and text refused, subfolders as stations in the agreed order, tracks known by SHA-1, and a file being uploaded held back until the folder stops changing.

## slimcore/src/test/java/studio/merami/slimcore/baseclasses/ChassisGeometryTest.java

- `void theCabinTestUsesTheCameraAndCarOfOneFrame()` — Why `CabinAcoustics` compares within one frame: the driver's eye of a G 63 at 120 km/h, put against the car's position a tick and a half later, is outside the cabin box.

## sliminterfacefabric/src/main/resources/assets/slimvehicle/shaders/core/vehicle_light.fsh

- `vehicle_light.fsh` — Every vehicle lamp as one light source, composited in a single pass. From the same beam come the light on surfaces (shadowed by blocks, cars and figures through the shadow maps), the glow of the beam in the air and the bloom of a lens facing the camera. Constants match VehicleLights and vehicle_light_shadow.fsh; VOLUME and TILES match LightVolume.
- `const float HEADLIGHT = 4000.0` — Intensity scales: dipped beam, the same beam scattered by the air, small lamps, lens blooms. Small lamps were raised from 1.5 to 2.6 with the wider lobe so tail and brake lights paint the road red behind the car.
- `const float BLOOM_ANGLE = 0.012;` — Bloom radii in blocks (`BLOOM_HEADLIGHT`, `BLOOM_LAMP`), never smaller than this angle, so a far lamp keeps a visible glow.
- `const float NEAR_LIGHT = 0.0025;` — Light of the dipped beam on the road just ahead of the car, where the even patch has not started yet.
- `const float CEILING = 1.12` — Lit surfaces approach this multiple of their colour, never a flat white.
- `float dippedBeam(vec3 local)` — Dipped beam: h sideways in degrees, t the tangent of the angle below the lamp axis. Under a soft cut-off a narrow core (gaussian width 13°) falls as 1/t^2.4, so on a flat road its light thins out gently with distance (as d^-0.6) instead of the old even patch (1/t^3) that ended in a visible edge; a weaker, wider flood (30°) lights the near road and the verges. The two lamps of a car give two long beams that merge ahead, as in the reference videos. A wide, weak `NEAR_LIGHT` fills the road from the bumper. 1 just under the cut-off. Checked against the reference frames with `.work/render/LightSim.java`, a CPU copy of this shader over a flat grass plane.
- `float hazeBeam(vec3 local)` — The beam as seen in the air: wider (16°) and taller (9°) than the road lobe and centred 3° below the axis, so from the side it reads as a soft cone like the reference beams.
- `const float RANGE = 48.0;` — Reach of a headlight in blocks, also the depth range of the shadow maps (vehicle_light_shadow.fsh). Raised from 36 with the softer beam: the light fades out from half the range, so the beam ends gradually instead of at a line.
- `float bloom(vec3 view, vec3 origin, float dist, float radius)` — Lens glow around a lamp facing the camera: a bright round core and a long soft halo, like the reference mods. The stepped pixel sprite before it read as a flat sticker on the lamp.
- `vec3 normalAt(ivec2 pixel, vec3 p)` — Normal from the depth buffer, taking the neighbour on the same surface on each axis so silhouette pixels keep their own face. Block faces are axis-aligned: clear cases snap for crisp shading.
- `float faceShade(vec3 n)` — Vanilla darkens block faces by direction; dividing it back out recovers the surface colour.
- `float surfaceVisibility(int slot, vec3 delta, vec3 local, vec3 normal, vec3 forward, vec3…` — 2x2 filtered shadow test. Each texel's depth is compared with where that texel's own ray meets the plane of the lit surface, so a beam grazing a flat road stays continuous instead of striping. That expected depth may differ from the pixel's own distance by `spread`: at a grazing angle one texel row covers several blocks of road, and the old fixed ±1.5 window took the nearer row for an occluder, which drew bright and dark bands across the far end of the beam. It is capped just under RANGE, the depth a texel that saw nothing stores.
- `float scattering(vec3 view, float maxT, vec3 origin, vec3 forward, vec3 right, vec3 up, i…` — Light scattered towards the camera along the view ray. The ray is first cut to the part that can lie in the beam: inside the lamp's range (a sphere, not the old ±RANGE slab along the ray) and inside the wedge in front of the lamp where `hazeBeam` is not negligible (`HAZE_SIDE`, `HAZE_BELOW`, `HAZE_ABOVE`). Samples are spread over that part by equi-angular sampling around the lamp, which cancels the 1/d^2 fall-off. Spreading six jittered samples over the whole ray, as before, left most of them outside the beam: seen from above or from the side the glow came out as coarse grain.
- `vec2 clipSpan(vec2 span, vec3 view, vec3 origin, vec3 n)` — Cuts the interval of t along the view ray to the half-space n·(view·t − origin) ≥ 0, a plane through the lamp.
- `const float HAZE_SIDE = 0.839;` — Haze wedge of the beam: tan 40° sideways, tan 25.5° below and tan 19.5° above the lamp axis, where `hazeBeam` has fallen to 0.2 % (2.5 of its gaussian widths around its centre 3° below the axis).
- `const float HAZE_REACH = 0.4;` — Seen from the side, the cone is lifted along its length (up to `HAZE_REACH_CAP` blocks from the lamp) so it reads like the reference beams rather than a streak at the lens. Looking along the beam (from the driver's seat, or into it from the front) the lift goes to zero, so the lamps do not blind.
- `const float HAZE_FORWARD = 0.55;` — Phase function: Henyey-Greenstein with g = 0.55 for `HAZE_FORWARD_SHARE` of the scattered light, the rest the same in every direction. Pure forward scattering (g = 0.6, as before) left the cone almost invisible from the side.
- `float lensVisibility(vec3 origin, float dist)` — The lens is only seen if nothing nearer covers it on screen. Its screen spot comes from `ViewProjection` and the camera-relative origin; the distances it compares are measured from `Eye`.
- `uniform vec3 Eye;` — The real eye in camera-relative space (`VehicleLights#render`): view bobbing moves it off the origin. Everything seen from the eye (the view ray, the scene distance, the haze along the ray, the lens glare, which side of a surface faces the viewer) is measured from it; surface positions and lamp positions stay camera-relative, which is what the depth reconstruction gives.
- `float solidFrom(int kind, float y0, float y1)` — Where the solid part of a block (LightVolume occupancy) starts along a segment through its cell that runs from height y0 to y1 inside the cell: 0..1 along the segment, or -1 if the segment misses it. The same function is in vehicle_light_shadow.fsh.
- `float traceBlocks(vec3 origin, vec3 dir, float limit)` — Free distance along a ray through the block volume, up to limit. The same walk as in vehicle_light_shadow.fsh; keep the two in step.
- `float openPath(vec3 p, vec3 normal, vec3 origin, float sceneDistance)` — Whether a lamp without a shadow map sees a surface point: a walk from just off the surface to the lamp. Small lamps and the headlights of cars past the shadowed ones used to light through walls.
- `vec3 lightmap(vec2 levels)` — The vanilla lightmap lookup (as `minecraft_sample_lightMap`): block and sky level 0..15, with its half-texel offset, so the colour matches what the frame was lit with.
- `bool surfaceLevels(vec3 p, vec3 normal, out vec2 levels)` — The light levels the game lit a surface with: the blocks in front of it in LightVolume, blended across the face like smooth lighting, with full blocks left out. False outside the volume or deep in a crevice.

### scattering(vec3,float,vec3,vec3,vec3,vec3,int,float)

- `float cosine = dot(dir, -view);` — Henyey-Greenstein: haze scatters mostly forward, so a beam aimed at the camera glows most.

### main()

- `vec3 normal = vec3(0.0, 1.0, 0.0);` — The normal takes four more depth reads; most pixels are out of every lamp's reach and never need it.
- `float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))…` — Interleaved gradient noise: jitters the haze samples and dithers the output against banding.
- `bool omni = slot <= -2;` — A fire: light every way, no lens.
- `if (e > 0.002 && !omni) e *= openPath(p, normal, origin, sceneDistance);` — Fires are not walked through the volume: they light a wide area around the car, and a walk per pixel for each of them cost a lot of frame rate with several burning cars.
- `float energy = dot(light, vec3(0.3, 0.59, 0.11));` — Bright light saturates by its brightness, not channel by channel, so it keeps its colour. Per channel a strong fire light on grass ran out of red first and then lit only green.
- `if (peak > room * 1.25) gain *= room * 1.25 / peak;` — The brightest channel is capped at 1.25 × room by scaling all three, which keeps the hue; clipping that channel alone brought the green back.
- `e = HEADLIGHT * dippedBeam(local) * lambert / (dist * dist + 1.0)` — Nothing right at the lens (the car's own bumper); from half the range the light fades out.
- `vec3 seen = origin - Eye;` — The lamp as the eye sees it, for the haze along the view ray and the lens bloom. With the unbobbed camera instead, the cone and the glare shook against the lamp at every step.
- `float lampDistance = length(seen);` — Lens bloom: brightest when the lamp faces the camera, more so from inside a headlight beam.
- `vec3 base = max(surfaceKnown ? lightmap(surfaceLight) : ambient / weight, vec3(0.035));` — The frame is already lit by the lightmap: divide the surface's own light (and the face shading) back out to get the surface colour, then add the lamp light on top with a soft ceiling. Outside the volume the light at the lamps' cars stands in for it. Using that everywhere, as before, turned surfaces lit by a torch or a street lamp white under the beam (their colour came out many times too bright and clipped), and dimmed the beam on dark surfaces when the car stood in light.
- `if (e > 0.002) e *= slot >= 0 ? surfaceVisibility(slot, delta, local, normal, forward, right, up)` — Headlights with a shadow map use it; the others walk the volume (`openPath`).
- `result = mix(result, 1.0 - 0.2 * exp(-(result - 0.8) / 0.2), step(0.8, result));` — Soft shoulder for the brightest values instead of a hard clip, then dither.

## sliminterfacefabric/src/main/resources/assets/slimvehicle/shaders/core/vehicle_light_shadow.fsh

- `vehicle_light_shadow.fsh` — Shadow maps of the shadow-casting lamps (headlights). Each lamp owns a 256x128 tile that stores the free distance along every direction of its frustum, traced through the block volume around the camera (LightVolume) and the boxes of nearby cars. Constants match VehicleLights and vehicle_light.fsh.
- `float traceBlocks(vec3 origin, vec3 dir, float limit)` — Grid walk (Amanatides & Woo). A partial block is hit where the ray crosses its height inside the cell (`solidFrom`). Stopping at the edge of the cell, as before, ended the free distance up to a block short of a slab, path, carpet or stair step, so those surfaces fell into their own shadow: dark patches and stripes under the beam. The same walk is in vehicle_light.fsh.
- `float solidFrom(int kind, float y0, float y1)` — See vehicle_light.fsh.
- `float traceCars(vec3 origin, vec3 dir, float car, float nearest)` — Other cars as yaw-rotated boxes, and people and animals as upright ones (id -1); a lamp never shadows its own car.

## build.gradle

- `sourceSets {` — One project with two source roots: the core and the Fabric interface are separate folders like Immersive's `mccore` and `mcinterface…`, but build into one jar and one mod. Separate Gradle projects would need a Loom setup per project and jar-in-jar for the core without changing what ships.
- `def gameTests = zoneTests ? [` — Game test classes added as `fabric-gametest` entrypoints when building with `-PvehicleTests`; with `-PzoneTests` as well, only `ZoneGameTests`, which need Mosvegas Core on the server.
- `test {` — JUnit tests of the core (`slimcore/src/test/java`): drivetrain, suspension, chassis geometry, damage and the built-in definitions, everything that needs no game. They run with every `build` (`test` task, JUnit 5.10.1) from the project root, where `TestPacks` reads the built-in pack. Game tests keep what needs a world: entities, blocks, players, collisions.
- `def unitTestCopy = new File(System.getProperty('java.io.tmpdir'), 'slimvehicle-unit-tests')` — The test worker runs from a copy of the compiled classes in the temp folder. Gradle hands the worker its classpath in an `@argfile` written in UTF-8, while the Java launcher on Windows reads argfiles in the ANSI code page: the Cyrillic project path came out garbled and not a single test class was found. Only the core's libraries and JUnit are on the classpath, so a test that reached for Minecraft would fail.
- `if (project.hasProperty('slimBuildDir')) layout.buildDirectory = file(project.property('slimBuildDir'))` — Separate outputs for verification builds. Not safe while a development client is open: when the output directory changes, Gradle deletes the previous compileJava classes, and the running game crashes on the next class it loads lazily.
- `tasks.register('checkCoreBoundary', JavaCompile) {` — Compiles `slimcore` on its own against Gson, JOML and slf4j only, before every `compileJava`: a core class that uses Minecraft, Fabric or anything else from the game does not compile. The text search for fully qualified names catches what an import check would miss (`net.minecraft.…` written inline).
- `options.compilerArgs << '-proc:none'` — No annotation processors (Mixin's would look for its config) for the core alone.
- `def zoneTests = project.hasProperty('zoneTests')` — `-PzoneTests` (with `-PvehicleTests`) makes `ZoneGameTests` the only game test class and puts Mosvegas Core on the runtime. The other game tests run without it: the core protects the overworld from terrain changes by default, which would keep cars from knocking blocks down in the smash tests.
- `modCompileOnly files('libs/compat/mosvegas-core.jar')` — Mosvegas Core (the city server's core mod; its build without SQLite, 0.2 MB) for `MosvegasZones`, compile only: the mod runs without it and only suggests it (`fabric.mod.json`). Replace the jar when the core's `TransportAccess` API changes.
- `modCompileOnly "maven.modrinth:lambdynamiclights:2.3.4+1.20.4"` — The LambDynamicLights API for `DynamicLightsCompat`, compile only: the mod runs without it, and its entrypoint is only loaded when LambDynamicLights is installed.
- `options.release = 17` — The mod targets Java 17 like Minecraft 1.20.4, whatever JDK builds it; newer language features would build here and fail on players' Java 17.
- `modLocalRuntime files(taczJar)` — The dev client runs with TaCZ (`libs/compat/tacz.jar`, the MosVegas build of TACZ-Refabricated 0.7.1, the same jar the compat code compiles against) and Forge Config API Port, which TaCZ requires. Only for the dev runs: the mod itself does not depend on TaCZ. Builds with `-PvehicleTests` leave it out (`withTacz`), so the Gradle game tests run on plain Fabric.
- `def taczLibs = layout.buildDirectory.dir('tacz-libs').get().asFile` — TaCZ ships its libraries inside its jar (MixinExtras 0.5, Cardinal Components, LuaJ, BCEL, Commons Math, simplebedrockmodel, and mae inside simplebedrockmodel), and Loom's dev runtime does not load nested jars of a mod dependency: the client crashed on TaCZ's MixinExtras expressions and then on a missing mae class. They are unpacked here, nested ones too, whenever the TaCZ jar is newer than the folder, and put on the dev runtime.
- `localRuntime "com.electronwill.night-config:core:3.6.6"` — Night Config is nested in Forge Config API Port for the same reason (TaCZ's own build adds it the same way).
- `modLocalRuntime "maven.modrinth:sodium:mc1.20.4-0.5.8"` — Dev client only (not a dependency of the mod): vanilla re-culls every chunk section whenever the view turns, which at high render distance stutters while steering. Players normally run Sodium.
- `programArgs '--username', 'TransportTester'` — The development player keeps its old name, so its inventory and position in the existing test worlds stay its own.

## tools/GenerateParticleTextures.java

- `public class GenerateParticleTextures` — Draws the mod's particle sprites into `assets/slimvehicle/textures/particle/` (or the folder given): `java tools/GenerateParticleTextures.java`. Everything comes from fixed seeds, so running it again gives the same files byte for byte; change a sprite here and regenerate rather than editing the PNGs. The frame counts and roles match the SnAssets Particles sheets they replaced (fire 7, smoke 12, clusters 5, dirt 4, dust 3, snow 2), so `VehicleParticles` and `particles/*.json` pick frames as before.
- `private static BufferedImage fire(int frame)` — A flame of ragged noise tongues over a hot base, coloured in six steps of `FIRE` from dark red at the edge to pale yellow in the core; the last frames shrink as it burns out. Lit by itself in game, so no shading.
- `private static BufferedImage burst(int frame)` — The backfire: a round, ragged fireball (`backfire_0..4`), white-hot in the middle and dark red at the rim, frames growing and cooling. The exhaust shoots a short row of them (`VehicleEffects#jet`); the tall fire tongues stay for burning cars (`particles/flame.json`).
- `private static BufferedImage smoke(int frame)` — The exhaust puff's life: a cluster of soft blobs that grows over frames 0..6, then erodes into holes and wisps. Lit from the top left by the field's gradient in four grey levels; the thin edge is half transparent.
- `double light = .6 + .4 * Math.max(-1, Math.min(1, lightX + lightY)) + .1 * (grain - .5);` — The gradient points into the cloud, so the side facing the light (top left) is where the field rises: the sign gives light there, not on the far side.
- `private static BufferedImage cluster(int variant)` — Larger (32 px) half-transparent smoke clouds for tyre smoke, five shapes, density stepped in fifths.
- `private static BufferedImage snow(int variant)` — A faint haze with white flakes, 32 px; dirt and dust are scattered specks and a soft round puff.

## tools/build.ps1

- `$javaRoot=@('D:\Work\Tools\jdk-21',$env:JAVA_HOME,` — JDK 21 from `D:\Work\Tools` first, then `JAVA_HOME`, then the Minecraft launcher's runtime.

## tools/ImportBlockbenchModel.java

- `public class ImportBlockbenchModel` — Turns a Blockbench project (.bbmodel with cubes, any model format) into the pack's Bedrock geometry and texture, regrouping the cubes into the bones a vehicle definition drives (doors, glass, wheels, steering wheel, lamps). Which cube goes where is a rig file in `tools/rigs/`; run it through `tools/import_model.ps1`. A new version of the same model is re-imported with the same rig.
- `static JsonObject exportCube(Cube cube, double[] paneUv)` — The same numbers as Blockbench's own Bedrock/GeckoLib export: X mirrored (origin from `-to.x`), pivot X and rotation X/Y negated, up/down UVs flipped both ways. `--compare <geo.json>` checks a plain export against one made by Blockbench cube by cube; the G63 v8 matched on all 558 cubes. A pane keeps only its `paneFace`, mapped onto its own mask (`paneUv`), without the face's UV rotation.
- `if (Math.abs(source[i] - target[i]) > 1e-4) {` — A cube may change bones only if the chain of pivots and rotations above it stays the same, so the rest pose can never change; a rig that would move a cube stops and names it.
- `static Map<Cube, double[]> paintPanes(JsonObject glass, List<Bone> bones)` — Blockbench windows are often fully transparent pixels with a few white streaks, which both render passes would leave invisible. Each pane cube gets its own region of the texture, at the texel density of its original face, painted opaque in `glass.color` where the window opening is and transparent elsewhere; `VehicleRenderer` darkens and fades it by the vehicle's tint (`/slimvehicle tint`). Regions go where no exported face reads and nothing is drawn yet. One shared uniform patch made every pane a full rectangle: the G 63's door windows are rectangles under a frame raked like the windshield, so their front top corners stood out in the air ahead of the pillar.
- `static boolean[][] paneMask(Cube pane, String key, int w, int h, List<Cube> solid)` — A texel of the pane face is kept when, walking from it within the pane's plane, all four ways (along both face axes, both signs) meet a solid cube before passing the pane's edge by `PANE_OVERHANG` pixels. A cube counts when the line through the point along the pane normal passes through it within `PANE_DEPTH` pixels of the plane, i.e. it is seen there from straight outside. Only solid cubes within `PANE_REACH` pixels of the pane take part. One open way is enough to drop the texel: that part of the glass sticks out of its frame.
- `static double[][] faceAxes(double[] from, double[] to, String key)` — Corner at texel (0, 0) and the cube-space steps to texel (w, 0) and (0, h): u runs left to right and v top to bottom as the face is seen from outside, Blockbench's convention for each face.
- `static String paneFace(double[] from, double[] to, JsonObject faces)` — A pane keeps one face: its outer side. The translucent pass draws faces from both sides, so the two big faces of a slab doubled the haze, and its edges showed as grey strips around every window.
- `static List<String> expand(String name)` — In a rig `Name_3..7` lists Name_3 to Name_7.
- `Map<Cube, Bone> placed = new IdentityHashMap<>();` — Cubes and groups point at each other, so they are compared by identity, not by their record hashes.

## tools/import_model.ps1

- `$gson = Get-ChildItem (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson')…` — The importer runs as a single source file with Gson from Gradle's cache, without Gradle, so it works while the dev client is open.

## tools/rigs/g63.json

- `"glass": {"color": "#607080"},` — Dark grey-blue: at the default tint the pane darkens what is behind it a little. A light patch at the same alpha whitened the cabin like frosted glass.
- `"name": "chassis", "parent": "g63", "pivot": [0, 11.2, 0]` — Pivot height is the definition's `body_pivot` (0.7) in pixels. The wheel bones hang from the root so the body leans over them.
- `"name": "mirror_fl", "parent": "door_fl", "pivot": [-19, 26.8, -14.25]` — Mirror heads moved out of the door so JSON animations can fold them about their mounts; they swing with the door.
- `"name": "steering_spin", "parent": "steering_mount", "pivot": [-9.6, 25, -6.2]` — Rim, spokes and hub in an untilted child of the tilted mount, so the wheel spins about its own axis (`axis: [0, 0, 1]` in g63.json); the column stays on the mount.
- `"name": "glovebox", "parent": "interior", "pivot": [10.2, 13.714, -13.978]` — Hinge on the lower edge of the tilted dashboard panel in front of the passenger.
- `"name": "wiper_left", "parent": "chassis", "pivot": [-2.4, 25.41, -16.69]` — Pivots at the right ends of the arms, as on the Volga: both blades park pointing left.
- `"name": "brakelight", "parent": "trunk", "like": "Trunk", "cubes": ["Trunk_4"]` — The third brake light, the red bar along the top of the tailgate, in a bone of its own so `lamps.glow` can light it (on braking); it hangs from the tailgate and swings with it. The tailgate's click and collision box is unchanged: `Trunk_2` and `Trunk_3` reach as high.
- `"name": "tail_strips", "parent": "chassis", "cubes": ["Body_77", "Body_78"]` — The two rear bumper blocks either side of the number plate. Their texture carries the red strips that glow with the tail lights; the rest of each block stays bumper (the glow mask keeps only red texels). In g63.json they keep the body's paint and dents.
- `"name": "headlights", "parent": "chassis", "cubes": [` — Lenses first, then the light rings, in the order `lamps.headlight_bone.cubes` of g63.json lists them.

## tools/RenderItemIcon.java

- `public class RenderItemIcon` — Draws a vehicle's 32×32 inventory icon from its own model, texture and glass list, three-quarter from the front left, so icons look alike and follow the model after a re-import. Same bone and cube transforms as `ImportBlockbenchModel` and GeckoLib.
- `static final int SAMPLES = 8;` — The model is drawn at 256×256 and each icon pixel averages its 8×8 block; a pixel less than 40 % covered stays empty, so the outline is not fringed with half-transparent pixels.
- `static final int[] GLASS = {78, 104, 128};` — Panes are drawn as tinted blue-grey glass whatever their texture holds: the Volga's windows are transparent pixels, the G 63's a mask painted for the in-game tint, and neither reads at 32 pixels.
- `if (edge) c[k] = (int) (c[k] * (k == 2 ? .5 : .45));` — A darker rim on the silhouette, as on vanilla item icons, keeps a black car visible on the slot grey.
- `case "--gloss" -> gloss = value;` — Added light on upward faces. Black paint has nothing for the shading to work with; the Volga icon uses 30, white cars none.

## tools/GenerateHornSound.java

- `public class GenerateHornSound` — Writes `sounds/horn.ogg`, the car horn loop, from scratch: `java --enable-preview --source 21 --enable-native-access=ALL-UNNAMED tools/GenerateHornSound.java <libsndfile.dll> <out.ogg>` (the libsndfile that ships with the sound tools in `.tools/python/_soundfile_data/`, called through the FFM API). The imported horn was a short one-shot with a fade, which could not be held and sounded odd.
- `static final double LOOP = .5;` — Half a second, and both tones (`LOW` 410 Hz, `HIGH` 512 Hz, a major third like a dual-tone car horn) make whole cycles in it, so the loop has no seam.
- `static final double[][] FORMANTS = {{1850, 650, 1}, {3300, 900, .55}, {5200, 1400, .2}};` — Resonances of the horn's trumpet over the harmonics of both tones; then a soft clip (`tanh`) for the brassy edge and normalising to 0.85.
- `static void encode(SymbolLookup lib, Path path, float[] samples)` — Mono 44.1 kHz OGG Vorbis through `sf_wchar_open`, so paths with Cyrillic letters work.

## tools/GenerateHudTextures.java

- `public class GenerateHudTextures` — Draws `textures/gui/dial_digits.png` and `hud_icons.png`: `java tools/GenerateHudTextures.java`. Edit the pixel rows here and regenerate rather than editing the PNGs.
- `static final String[][] DIGITS = {` — 4×6 digits in the style of the customer's dial (their printed numbers were the model; 3, 5, 7 and 9 drawn to match), stamped on the dial faces by `DialFace`.
- `static final String[][] ICONS = {` — 9×9 white icons in `CELL` columns, in `VehicleHud`'s index order: headlight, fuel, body, handbrake, lock, radio, wipers, engine, siren, left and right arrows, key. White, so the HUD tints them per state.

## tools/import_immersive_sounds.ps1

- `param([string[]]$Only)` — `-Only v8_idle,v8_low` fetches just those events and keeps the other records and files as they are, so adding sounds does not download and re-encode the ones already in the mod. The horn is no longer imported: `GenerateHornSound.java` makes it.
- `v8_idle='enginefordfe428_idle'; v8_low=…` — The Ford FE 428 V8 of the official pack, the only car V8 there, for the G 63. `mediumwell` is its on-throttle mid range; `mediumrare` (off-throttle) is left out because vehicles do not sync the throttle to clients.

## tools/DownmixSounds.java

- `public class DownmixSounds` — Replaces `normalize_vehicle_sounds.py` now that there is no Python on the build machine: the same libsndfile (`.tools/python/_soundfile_data`) through Java's foreign function API, so it needs `--enable-preview` on Java 21. Minecraft plays only mono sounds at a position; a stereo file plays at full volume wherever the listener is. Stereo files are kept in `.work/immersive-original-audio` and downmixed; mono ones are left untouched, and every record gets its file's hash and length.
- `static MemorySegment openFile(Arena arena, Path path, int mode, MemorySegment info)` — `sf_wchar_open`: the project path is Cyrillic, which the narrow `sf_open` cannot open on Windows.
- `static final int CHUNK = 4096;` — libsndfile's Windows Vorbis encoder needs bounded input writes.
- `entry.addProperty("durationSeconds", …` — Rounded half to even from the exact double, as Python's `round` did, so records written by the old script keep their values.

## tools/prepare_model.py

- `for old, name in [('wheel2','fl'),('wheel3','fr'),('wheel5','rl'),('wheel4','rr')]:` — Each wheel has three distinct transforms: steering, static camber, rolling.
- `bones += [dict(name=f'tyre_{name}',parent=b['name'],pivot=pivot,cubes=cubes[:8]),` — First eight segments form the tyre, last two form the rim/hub.
- `cubes=[body['cubes'][i] for i in indices]` — Original indices precede the extracted glovebox.
- `bones.append(dict(name='chassis',parent='Volga',pivot=[2,12,7]))` — The chassis rocks about its centre of mass; wheels stay on their suspension mounts.
- `SCALE = .8` — 0.8 gives enough cabin width for two Minecraft players; keep the imported proportions and export all geometry in exactly the same coordinate system.

## tools/refine_vehicle_assets.py

- `spin['pivot'] = [round(hub['origin'][i]+hub['size'][i]/2,6) for i in range(3)]` — Spin through the centre of the hub, leaving its column attachment fixed.
