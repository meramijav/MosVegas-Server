package ru.mosvegas.core.data;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Dependency-free regression suite, run by Gradle migrationTest/check. The real sqlite-jdbc (test classpath only)
 * writes the bases, and SqliteReader must return exactly what SQLite itself returns.
 */
public final class DataStoreMigrationTest {
    private static final List<String> OLD_SCHEMA = List.of(
        "CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, bank_balance INTEGER NOT NULL, legacy_cash INTEGER NOT NULL, initialized INTEGER NOT NULL, first_name TEXT, last_name TEXT, gender TEXT, age INTEGER, passport_issued_at INTEGER, faction TEXT, faction_level INTEGER NOT NULL, hierarchy_level INTEGER NOT NULL, faction_xp INTEGER NOT NULL, server_level INTEGER NOT NULL, server_level_ticks INTEGER NOT NULL, last_salary_at INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS player_licences (player_uuid TEXT NOT NULL, licence TEXT NOT NULL, PRIMARY KEY(player_uuid, licence), FOREIGN KEY(player_uuid) REFERENCES players(uuid) ON DELETE CASCADE)",
        "CREATE TABLE IF NOT EXISTS active_quests (player_uuid TEXT NOT NULL, template_id TEXT NOT NULL, point_index INTEGER NOT NULL, progress_ticks INTEGER NOT NULL, chosen_item TEXT, accepted_at INTEGER NOT NULL, PRIMARY KEY(player_uuid, template_id), FOREIGN KEY(player_uuid) REFERENCES players(uuid) ON DELETE CASCADE)",
        "CREATE TABLE IF NOT EXISTS completed_starter_quests (player_uuid TEXT NOT NULL, template_id TEXT NOT NULL, PRIMARY KEY(player_uuid, template_id), FOREIGN KEY(player_uuid) REFERENCES players(uuid) ON DELETE CASCADE)",
        "CREATE TABLE IF NOT EXISTS completed_faction_quests (player_uuid TEXT NOT NULL, template_id TEXT NOT NULL, PRIMARY KEY(player_uuid, template_id), FOREIGN KEY(player_uuid) REFERENCES players(uuid) ON DELETE CASCADE)",
        "CREATE TABLE IF NOT EXISTS properties (id TEXT PRIMARY KEY, owner_uuid TEXT, tenant_uuid TEXT, for_rent INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS property_members (property_id TEXT NOT NULL, player_uuid TEXT NOT NULL, PRIMARY KEY(property_id, player_uuid), FOREIGN KEY(property_id) REFERENCES properties(id) ON DELETE CASCADE)",
        "CREATE TABLE IF NOT EXISTS permissions (player_uuid TEXT NOT NULL, node TEXT NOT NULL, PRIMARY KEY(player_uuid, node))");
    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes()), BOB = UUID.nameUUIDFromBytes("bob".getBytes());
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("mosvegas-migration");
        try {
            for (int pageSize : new int[]{512, 1024, 4096, 65536}) readerMatchesSqlite(root.resolve("reader-" + pageSize), pageSize);
            importsOldWorld(root.resolve("source"), root.resolve("world"));
            recoversDamagedJson(root.resolve("damaged"));
        } finally {
            try (var files = Files.walk(root)) { files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete()); }
        }
        System.out.println("DataStoreMigrationTest: " + assertions + " assertions passed");
    }

    /** 0.0.4 never closed its connection, so a server's base keeps recent saves in the WAL next to stale and unfinished frames. */
    private static void readerMatchesSqlite(Path dir, int pageSize) throws Exception {
        Files.createDirectories(dir);
        Path live = dir.resolve("live.db"), copy = dir.resolve("copy.db");
        Random random = new Random(pageSize);
        Map<String, List<Map<String, Object>>> committed;
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + live)) {
            execute(c, "PRAGMA page_size=" + pageSize, "PRAGMA journal_mode=WAL", "PRAGMA wal_autocheckpoint=0",
                "CREATE TABLE IF NOT EXISTS \"values\" (id TEXT PRIMARY KEY, \"small, int\" INTEGER, [big] INTEGER NOT NULL DEFAULT 0, real REAL, `text` TEXT CHECK(length(`text`) > 0), data BLOB, UNIQUE(id, big))",
                "CREATE TABLE members (owner TEXT NOT NULL, member TEXT NOT NULL, PRIMARY KEY(owner, member), FOREIGN KEY(owner) REFERENCES \"values\"(id))");
            insert(c, random, 0, 3000);
            execute(c, "DELETE FROM \"values\" WHERE rowid % 3 = 0", "ALTER TABLE members ADD COLUMN note TEXT");
            insert(c, random, 3000, 3500);
            // After a checkpoint the next writer restarts the WAL with new salts: the longer old tail must be ignored.
            execute(c, "PRAGMA wal_checkpoint");
            insert(c, random, 3500, 3520);
            execute(c, "UPDATE members SET note = 'заметка' WHERE rowid % 2 = 0");
            committed = dump(c);
            Path wal = dir.resolve("live.db-wal");
            byte[] walBefore = Files.readAllBytes(wal);
            c.setAutoCommit(false);
            execute(c, "PRAGMA cache_size=4");
            insert(c, random, 3520, 6000);
            yes(!Arrays.equals(Files.readAllBytes(wal), walBefore), "unfinished transaction spilled into the WAL");
            Files.copy(live, copy);
            Files.copy(wal, dir.resolve("copy.db-wal"));
            c.rollback();
        }
        same(new SqliteReader(copy), committed, "WAL, page size " + pageSize);
        yes(Files.notExists(dir.resolve("live.db-wal")), "closing merged the WAL");
        same(new SqliteReader(live), committed, "main file, page size " + pageSize);
    }

    private static void insert(Connection c, Random random, int from, int to) throws SQLException {
        long[] edges = {0, 1, -1, 127, -128, 32767, -32768, 8388607, -8388608, Integer.MAX_VALUE, Integer.MIN_VALUE, (1L << 47) - 1, -(1L << 47), Long.MAX_VALUE, Long.MIN_VALUE};
        try (PreparedStatement v = c.prepareStatement("INSERT INTO \"values\" VALUES(?,?,?,?,?,?)"); PreparedStatement m = c.prepareStatement("INSERT INTO members(owner, member) VALUES(?,?)")) {
            for (int i = from; i < to; i++) {
                v.setString(1, "id-" + i);
                if (i % 7 == 0) v.setNull(2, Types.INTEGER); else v.setLong(2, edges[i % edges.length]);
                v.setLong(3, random.nextLong() >> random.nextInt(64));
                v.setDouble(4, random.nextGaussian() * 1e6);
                v.setString(5, i % 50 == 0 ? "Ж😀".repeat(1 + random.nextInt(4000)) : "Квартира №" + i);
                if (i % 5 == 0) v.setNull(6, Types.BLOB);
                else { byte[] data = new byte[1 + random.nextInt(i % 97 == 0 ? 20000 : 40)]; random.nextBytes(data); v.setBytes(6, data); }
                v.addBatch();
                for (int k = 0; k < i % 4; k++) { m.setString(1, "id-" + i); m.setString(2, UUID.randomUUID().toString()); m.addBatch(); }
            }
            v.executeBatch();
            m.executeBatch();
        }
    }

    private static Map<String, List<Map<String, Object>>> dump(Connection c) throws SQLException {
        Map<String, List<Map<String, Object>>> tables = new HashMap<>();
        for (String table : List.of("values", "members")) {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM \"" + table + "\" ORDER BY rowid")) {
                while (r.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
                        Object value = r.getObject(i);
                        row.put(r.getMetaData().getColumnName(i), value instanceof Integer n ? Long.valueOf(n) : value);
                    }
                    rows.add(row);
                }
            }
            tables.put(table, rows);
        }
        return tables;
    }

    private static void same(SqliteReader reader, Map<String, List<Map<String, Object>>> expected, String where) throws Exception {
        for (var table : expected.entrySet()) {
            List<Map<String, Object>> actual = reader.rows(table.getKey());
            yes(actual.size() == table.getValue().size(), where + ": " + table.getKey() + " has " + actual.size() + " rows, SQLite " + table.getValue().size());
            for (int i = 0; i < actual.size(); i++) {
                Map<String, Object> want = table.getValue().get(i), got = actual.get(i);
                yes(want.keySet().equals(got.keySet()), where + ": columns " + got.keySet() + " vs " + want.keySet());
                for (String column : want.keySet()) yes(Objects.deepEquals(want.get(column), got.get(column)), where + ": " + table.getKey() + " row " + i + " column " + column);
            }
        }
    }

    /** The exact tables and saves of 0.0.4, with the process gone before the WAL was merged. */
    private static void importsOldWorld(Path source, Path world) throws Exception {
        Files.createDirectories(source);
        Files.createDirectories(world);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + source.resolve(DataStore.DATABASE))) {
            execute(c, "PRAGMA foreign_keys=ON", "PRAGMA journal_mode=WAL", "PRAGMA synchronous=NORMAL");
            execute(c, OLD_SCHEMA.toArray(String[]::new));
            execute(c, "INSERT OR REPLACE INTO schema_meta(key,value) VALUES('schema_version','2')");
            oldSave(c, 1);
            oldSave(c, 12345);
            for (String suffix : new String[]{"", "-wal", "-shm"}) {
                Path part = source.resolve(DataStore.DATABASE + suffix);
                if (Files.exists(part)) Files.copy(part, world.resolve(part.getFileName()));
            }
            yes(Files.size(world.resolve(DataStore.DATABASE + "-wal")) > 0, "old world keeps its saves in the WAL");
        }
        DataStore.load(world);
        yes(Files.exists(world.resolve(DataStore.FILE)), "JSON written");
        yes(Files.notExists(world.resolve(DataStore.DATABASE)) && Files.notExists(world.resolve(DataStore.DATABASE + "-wal")), "base moved away");
        yes(Files.exists(world.resolve(DataStore.DATABASE + ".migrated")) && Files.exists(world.resolve(DataStore.DATABASE + "-wal.migrated")), "base kept as .migrated");
        checkOldWorld("import");
        DataStore.player(ALICE).bankBalance = 777;
        DataStore.save();
        DataStore.flush();
        DataStore.load(world);
        yes(DataStore.player(ALICE).bankBalance == 777, "JSON saved after import");
        DataStore.player(ALICE).bankBalance = 12345;
        checkOldWorld("JSON");
    }

    /** One autosave of 0.0.4: everything deleted and written again in one transaction. */
    private static void oldSave(Connection c, long balance) throws SQLException {
        c.setAutoCommit(false);
        execute(c, "DELETE FROM player_licences", "DELETE FROM active_quests", "DELETE FROM completed_starter_quests", "DELETE FROM completed_faction_quests",
            "DELETE FROM players", "DELETE FROM property_members", "DELETE FROM properties", "DELETE FROM permissions");
        try (PreparedStatement p = c.prepareStatement("INSERT INTO players VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"); PreparedStatement l = c.prepareStatement("INSERT INTO player_licences VALUES(?,?)")) {
            Object[] alice = {ALICE.toString(), balance, 50L, true, "Иван", "Петров", "male", 25, 1700000000000L, "police", 3, 2, 150, 7, 123456789012L, 1760000000000L};
            Object[] bob = {BOB.toString(), 0L, 0L, true, null, null, null, 0, 0L, null, 0, 0, 0, 0, 0L, 0L};
            for (Object[] row : new Object[][]{alice, bob}) { for (int i = 0; i < row.length; i++) p.setObject(i + 1, row[i]); p.addBatch(); }
            for (int i = 0; i < 2000; i++) {
                String id = UUID.nameUUIDFromBytes(("player" + i).getBytes()).toString();
                Object[] row = {id, (long) i, 0L, true, "Имя" + i, "Фамилия" + i, "female", 18 + i % 50, 1700000000000L + i, i % 3 == 0 ? "medics" : null, i % 5, i % 4, i, i % 30, (long) i * 1000, 1760000000000L};
                for (int k = 0; k < row.length; k++) p.setObject(k + 1, row[k]);
                p.addBatch();
                l.setString(1, id); l.setString(2, "driver"); l.addBatch();
            }
            p.executeBatch();
            for (String licence : List.of("weapon", "driver")) { l.setString(1, ALICE.toString()); l.setString(2, licence); l.addBatch(); }
            l.executeBatch();
        }
        execute(c, "INSERT INTO active_quests VALUES('" + ALICE + "','deliver',2,40,'minecraft:diamond{display:{Name:''\"Груз\"''}}',1760000000001)",
            "INSERT INTO completed_starter_quests VALUES('" + ALICE + "','intro')", "INSERT INTO completed_faction_quests VALUES('" + ALICE + "','patrol')",
            "INSERT INTO properties VALUES('flat_1','" + ALICE + "','" + BOB + "',1)", "INSERT INTO properties VALUES('parking_2',NULL,NULL,0)",
            "INSERT INTO property_members VALUES('flat_1','" + BOB + "')",
            "INSERT INTO permissions VALUES('" + ALICE + "','mosvegas.admin')", "INSERT INTO permissions VALUES('" + ALICE + "','mosvegas.admin.properties.edit')");
        c.commit();
        c.setAutoCommit(true);
    }

    private static void checkOldWorld(String stage) {
        PlayerData alice = DataStore.player(ALICE), bob = DataStore.player(BOB);
        yes(alice.bankBalance == 12345 && alice.cash == 50 && alice.initialized, stage + ": latest save, money");
        yes(alice.passport != null && alice.passport.firstName.equals("Иван") && alice.passport.lastName.equals("Петров") && alice.passport.gender.equals("male")
            && alice.passport.age == 25 && alice.passport.issuedAt == 1700000000000L, stage + ": passport");
        yes("police".equals(alice.faction) && alice.factionLevel == 3 && alice.hierarchyLevel == 2 && alice.factionXp == 150, stage + ": faction");
        yes(alice.serverLevel == 7 && alice.serverLevelTicks == 123456789012L && alice.lastSalaryAt == 1760000000000L, stage + ": progress");
        yes(alice.licences.equals(Set.of("weapon", "driver")), stage + ": licences");
        yes(alice.activeQuests.size() == 1 && alice.activeQuests.get(0).templateId.equals("deliver") && alice.activeQuests.get(0).pointIndex == 2
            && alice.activeQuests.get(0).progressTicks == 40 && alice.activeQuests.get(0).chosenItem.equals("minecraft:diamond{display:{Name:'\"Груз\"'}}")
            && alice.activeQuests.get(0).acceptedAt == 1760000000001L, stage + ": active quest");
        yes(alice.completedStarterQuests.equals(Set.of("intro")) && alice.completedFactionQuests.equals(Set.of("patrol")), stage + ": completed quests");
        yes(bob.initialized && bob.passport == null && bob.faction == null && bob.licences.isEmpty() && bob.activeQuests.isEmpty(), stage + ": player without passport");
        for (int i = 0; i < 2000; i += 199) {
            PlayerData d = DataStore.player(UUID.nameUUIDFromBytes(("player" + i).getBytes()));
            yes(d.initialized && d.bankBalance == i && d.passport.firstName.equals("Имя" + i) && d.licences.equals(Set.of("driver"))
                && Objects.equals(d.faction, i % 3 == 0 ? "medics" : null) && d.serverLevelTicks == i * 1000L, stage + ": player " + i);
        }
        var flat = DataStore.properties().get("flat_1");
        yes(flat != null && ALICE.toString().equals(flat.owner) && BOB.toString().equals(flat.tenant) && flat.forRent && flat.members.equals(Set.of(BOB.toString())), stage + ": property");
        var parking = DataStore.properties().get("parking_2");
        yes(parking != null && parking.owner == null && parking.tenant == null && !parking.forRent && parking.members.isEmpty(), stage + ": free property");
        yes(DataStore.permissions().get(ALICE.toString()).equals(Set.of("mosvegas.admin", "mosvegas.admin.properties.edit")), stage + ": permissions");
    }

    private static void recoversDamagedJson(Path world) throws Exception {
        Files.createDirectories(world);
        Path json = world.resolve(DataStore.FILE), backup = world.resolve(DataStore.FILE + ".bak");
        DataStore.load(world);
        DataStore.property("flat").owner = "first";
        DataStore.flush();
        DataStore.property("flat").owner = "second";
        DataStore.save();
        DataStore.flush();
        Files.writeString(json, "{\"players\":");
        DataStore.load(world);
        yes("first".equals(DataStore.properties().get("flat").owner), "damaged JSON falls back to .bak");
        yes(Files.readString(world.resolve(DataStore.FILE + ".broken")).equals("{\"players\":"), "damaged JSON kept as .broken");
        Files.writeString(json, "{ broken");
        Files.writeString(backup, "{ broken");
        try {
            DataStore.load(world);
            yes(false, "unreadable data stops loading");
        } catch (IllegalStateException expected) { assertions++; }
        DataStore.property("flat");
        DataStore.save();
        DataStore.flush();
        yes(Files.readString(json).equals("{ broken"), "unreadable data is never overwritten");
    }

    private static void execute(Connection c, String... sql) throws SQLException {
        try (Statement s = c.createStatement()) { for (String statement : sql) s.execute(statement); }
    }

    private static void yes(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
