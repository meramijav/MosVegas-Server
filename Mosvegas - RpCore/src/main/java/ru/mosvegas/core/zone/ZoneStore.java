package ru.mosvegas.core.zone;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import ru.mosvegas.core.data.JsonFile;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static ru.mosvegas.core.data.JsonFile.require;

public final class ZoneStore {
    public static final class State {
        public int version = 1;
        public Set<String> protectedWorlds = new LinkedHashSet<>(Set.of("minecraft:overworld"));
        public List<ZoneDefinition> zones = new ArrayList<>();
        /** Registry IDs for modded blocks which do not inherit vanilla block classes. */
        public Set<String> doorBlocks = new LinkedHashSet<>();
        public Set<String> containerBlocks = new LinkedHashSet<>();
    }
    private static Path path;
    private static State state = new State();
    private static ZoneIndex index = new ZoneIndex(List.of());
    private ZoneStore() {}
    public static State get() { return state; }
    public static ZoneIndex index() { return index; }

    public static void start(MinecraftServer server) {
        path = server.getSavePath(WorldSavePath.ROOT).resolve("mosvegas-zones.json");
        try {
            if (Files.notExists(path)) JsonFile.write(path, new State());
            reload();
        } catch (Exception e) { throw new IllegalStateException("Нельзя безопасно загрузить защиту зон: " + path, e); }
    }

    public static void reload() throws Exception {
        State loaded = JsonFile.read(path, State.class);
        validate(loaded);
        ZoneIndex nextIndex = new ZoneIndex(loaded.zones);
        state = loaded;
        index = nextIndex;
    }

    public static void update(Consumer<State> edit) throws Exception {
        State next = JsonFile.copy(state, State.class);
        edit.accept(next);
        validate(next);
        ZoneIndex nextIndex = new ZoneIndex(next.zones);
        JsonFile.write(path, next);
        state = next;
        index = nextIndex;
    }

    public static ZoneDefinition find(State source, String id) {
        return source.zones.stream().filter(z -> z.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Зона не найдена: " + id));
    }

    private static void validate(State value) {
        require(value.version == 1, "Неизвестная версия зон.");
        require(value.protectedWorlds != null && value.zones != null && value.doorBlocks != null
                && value.containerBlocks != null, "Поля зон не могут быть null.");
        for (String world : value.protectedWorlds) require(Identifier.tryParse(world) != null, "Неверное измерение: " + world);
        Set<String> ids = new HashSet<>();
        for (ZoneDefinition z : value.zones) {
            require(z != null, "Пустая зона.");
            JsonFile.validId(z.id);
            require(ids.add(z.id), "Повтор ID зоны: " + z.id);
            require(z.dimension != null && Identifier.tryParse(z.dimension) != null, "Неверное измерение зоны.");
            require(z.minX <= z.maxX && z.minY <= z.maxY && z.minZ <= z.maxZ, "Перепутаны границы: " + z.id);
            require(z.flags != null && z.players != null && z.factions != null, "Пустые настройки зоны.");
            z.flags.forEach((action, rule) -> {
                require(action != null && rule != null, "Неизвестный флаг/режим в " + z.id);
                require(action != ZoneAction.TERRAIN || rule != ZoneDefinition.Rule.MEMBERS, "terrain: только allow/deny.");
            });
            validateAccess(z.players, true);
            validateAccess(z.factions, false);
        }
    }

    private static void validateAccess(Map<ZoneAction, Set<String>> grants, boolean players) {
        grants.forEach((action, entries) -> {
            require(action != null && entries != null, "Неверный список доступа.");
            for (String entry : entries) {
                if (players) UUID.fromString(entry); else JsonFile.validId(entry);
            }
        });
    }
}
