package ru.mosvegas.core.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import ru.mosvegas.core.MosvegasCore;
import ru.mosvegas.core.config.ConfigManager;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * World-scoped storage. Services work against the in-memory model and autosave writes it to one JSON file
 * (temporary file, .bak, atomic move), so adding a new domain does not couple it to game ticks.
 */
public final class DataStore {
    static final String FILE = "mosvegas-core-data.json", DATABASE = "mosvegas-core.db";
    private static State state = new State();
    private static Path file;
    private static boolean dirty;

    private DataStore() {}

    public static void load(MinecraftServer server) { load(server.getSavePath(WorldSavePath.ROOT)); }

    /** Data that cannot be read stops the server, as zones do: a save over it would erase balances and owners. */
    static void load(Path world) {
        file = null;
        Path json = world.resolve(FILE), database = world.resolve(DATABASE);
        try {
            // An import renames the base away, so one still here is newer than the JSON. After a rollback 0.0.4 imports this JSON itself.
            if (Files.exists(database)) {
                importDatabase(database);
                normalize();
                JsonFile.write(json, state);
                // The base goes first: without it the next start reads the JSON written above.
                for (String suffix : new String[]{"", "-wal", "-shm"}) {
                    Path part = world.resolve(DATABASE + suffix);
                    if (Files.exists(part)) Files.move(part, part.resolveSibling(part.getFileName() + ".migrated"), StandardCopyOption.REPLACE_EXISTING);
                }
                MosvegasCore.LOGGER.info("SQLite-база {} перенесена в {}", database, json);
            } else {
                state = Files.exists(json) ? readJson(json) : new State();
                normalize();
            }
        } catch (Exception e) {
            state = new State();
            throw new IllegalStateException("Нельзя безопасно загрузить данные Mosvegas Core: " + world, e);
        }
        file = json;
        dirty = false;
    }

    /** A damaged file falls back to the .bak of the previous save and is kept as .broken. */
    private static State readJson(Path json) throws Exception {
        try {
            return JsonFile.read(json, State.class);
        } catch (Exception damaged) {
            Path backup = json.resolveSibling(json.getFileName() + ".bak");
            if (Files.notExists(backup)) throw damaged;
            State restored;
            try { restored = JsonFile.read(backup, State.class); } catch (Exception e) { e.addSuppressed(damaged); throw e; }
            Files.copy(json, json.resolveSibling(json.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(backup, json, StandardCopyOption.REPLACE_EXISTING);
            MosvegasCore.LOGGER.warn("{} повреждён и сохранён как .broken, данные восстановлены из .bak", json, damaged);
            return restored;
        }
    }

    /** Worlds from 0.0.4 and earlier keep their data in SQLite tables of the same shape as State. */
    private static void importDatabase(Path database) throws IOException {
        SqliteReader db = new SqliteReader(database);
        state = new State();
        for (var r : db.rows("players")) {
            PlayerData d = new PlayerData();
            d.bankBalance=number(r,"bank_balance"); d.cash=number(r,"legacy_cash"); d.initialized=number(r,"initialized")!=0;
            if (r.get("first_name") != null) {
                d.passport=new PlayerData.Passport(); d.passport.firstName=text(r,"first_name"); d.passport.lastName=text(r,"last_name");
                d.passport.gender=text(r,"gender"); d.passport.age=(int)number(r,"age"); d.passport.issuedAt=number(r,"passport_issued_at");
            }
            d.faction=text(r,"faction"); d.factionLevel=(int)number(r,"faction_level"); d.hierarchyLevel=(int)number(r,"hierarchy_level");
            d.factionXp=(int)number(r,"faction_xp"); d.serverLevel=(int)number(r,"server_level"); d.serverLevelTicks=number(r,"server_level_ticks"); d.lastSalaryAt=number(r,"last_salary_at");
            state.players.put(text(r,"uuid"), d);
        }
        for (var r : db.rows("player_licences")) playerByKey(text(r,"player_uuid")).licences.add(text(r,"licence"));
        for (var r : db.rows("active_quests")) {
            PlayerData.ActiveQuest q=new PlayerData.ActiveQuest(); q.templateId=text(r,"template_id"); q.pointIndex=(int)number(r,"point_index");
            q.progressTicks=(int)number(r,"progress_ticks"); q.chosenItem=text(r,"chosen_item"); q.acceptedAt=number(r,"accepted_at");
            playerByKey(text(r,"player_uuid")).activeQuests.add(q);
        }
        for (var r : db.rows("completed_starter_quests")) playerByKey(text(r,"player_uuid")).completedStarterQuests.add(text(r,"template_id"));
        for (var r : db.rows("completed_faction_quests")) playerByKey(text(r,"player_uuid")).completedFactionQuests.add(text(r,"template_id"));
        for (var r : db.rows("properties")) {PropertyRecord p=new PropertyRecord();p.owner=text(r,"owner_uuid");p.tenant=text(r,"tenant_uuid");p.forRent=number(r,"for_rent")!=0;state.properties.put(text(r,"id"),p);}
        for (var r : db.rows("property_members")) property(text(r,"property_id")).members.add(text(r,"player_uuid"));
        for (var r : db.rows("permissions")) state.permissions.computeIfAbsent(text(r,"player_uuid"), ignored->new LinkedHashSet<>()).add(text(r,"node"));
    }

    private static String text(Map<String,Object> row, String column) { Object v=row.get(column); return v==null?null:v.toString(); }
    private static long number(Map<String,Object> row, String column) { return row.get(column) instanceof Number n ? n.longValue() : 0; }

    private static PlayerData playerByKey(String key) { return state.players.computeIfAbsent(key, ignored->new PlayerData()); }

    private static void normalize() {
        if(state.players==null)state.players=new LinkedHashMap<>(); if(state.properties==null)state.properties=new LinkedHashMap<>(); if(state.permissions==null)state.permissions=new LinkedHashMap<>();
        for(PlayerData d:state.players.values()){if(d.licences==null)d.licences=new LinkedHashSet<>();if(d.activeQuests==null)d.activeQuests=new ArrayList<>();if(d.completedStarterQuests==null)d.completedStarterQuests=new LinkedHashSet<>();if(d.completedFactionQuests==null)d.completedFactionQuests=new LinkedHashSet<>();d.licences.remove("id");}
        for(PropertyRecord r:state.properties.values())if(r.members==null)r.members=new LinkedHashSet<>();
    }

    public static void save() { dirty = true; }

    public static synchronized void flush() {
        if (!dirty || file == null) return;
        try {
            JsonFile.write(file, state);
            dirty = false;
        } catch (Exception e) {
            MosvegasCore.LOGGER.error("Не удалось сохранить данные Mosvegas Core", e);
        }
    }

    public static PlayerData player(UUID id) {
        PlayerData data=playerByKey(id.toString());
        if(!data.initialized){data.bankBalance=ConfigManager.get().startingBankBalance;data.lastSalaryAt=System.currentTimeMillis();data.initialized=true;dirty=true;}
        return data;
    }
    public static PropertyRecord property(String id){PropertyRecord r=state.properties.get(id);if(r==null){r=new PropertyRecord();state.properties.put(id,r);dirty=true;}return r;}
    public static void deleteProperty(String id){state.properties.remove(id);dirty=true;}
    /** Owner, tenant, rent flag and members follow the object to its new ID. */
    public static void renameProperty(String from,String to){PropertyRecord r=state.properties.remove(from);state.properties.put(to,r==null?new PropertyRecord():r);dirty=true;}
    public static Map<String,PropertyRecord> properties(){return state.properties;}
    public static Map<String,Set<String>> permissions(){return state.permissions;}

    public static final class State {
        public Map<String,PlayerData> players=new LinkedHashMap<>();
        public Map<String,PropertyRecord> properties=new LinkedHashMap<>();
        public Map<String,Set<String>> permissions=new LinkedHashMap<>();
    }
    public static final class PropertyRecord { public String owner; public String tenant; public boolean forRent; public Set<String> members=new LinkedHashSet<>(); }
}
