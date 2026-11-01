package ru.mosvegas.core.zone;

import java.util.*;

/** Dependency-free regression suite, run by Gradle policyTest/check. */
public final class ZonePolicyTest {
    private static int assertions;
    public static void main(String[] args) {
        ZoneDefinition police = zone("police", -20, -20, 20, 20, 10);
        police.flags.put(ZoneAction.DOORS, ZoneDefinition.Rule.MEMBERS);
        police.factions.put(ZoneAction.DOORS, Set.of("police"));
        police.players.put(ZoneAction.DOORS, Set.of("alice"));
        yes(allowed(List.of(police), "bob", Set.of("police")), "faction access");
        yes(allowed(List.of(police), "alice", Set.of()), "individual access");
        no(allowed(List.of(police), "bob", Set.of("medics")), "foreign faction denied");
        no(allowed(List.of(police), null, Set.of()), "automation has no membership");
        police.flags.put(ZoneAction.DOORS, ZoneDefinition.Rule.DENY);
        no(allowed(List.of(police), "alice", Set.of("police")), "deny overrides membership");
        ZoneDefinition publicArea = zone("public", -100, -100, 100, 100, 0);
        publicArea.flags.put(ZoneAction.DOORS, ZoneDefinition.Rule.ALLOW);
        no(allowed(List.of(publicArea, police), "bob", Set.of()), "inner priority");
        publicArea.priority = 10;
        no(allowed(List.of(publicArea, police), "bob", Set.of()), "tie restricts");
        no(allowed(List.of(police, publicArea), "bob", Set.of()), "tie independent of order");
        publicArea.priority = 11;
        yes(allowed(List.of(police, publicArea), "bob", Set.of()), "explicit higher allow");
        publicArea.flags.clear();
        no(allowed(List.of(police, publicArea), "bob", Set.of()), "missing flag inherits by action");
        yes(ZoneIndex.permits(List.of(police), ZoneAction.SHOOT, "bob", Set.of(), true), "unrelated access does not affect shooting");
        no(ZoneIndex.permits(List.of(), ZoneAction.BUILD, "bob", Set.of(), false), "city fallback");
        yes(police.contains(-20, -64, -20), "minimum included");
        yes(police.contains(20.999, 319.999, 20.999), "whole maximum block included");
        no(police.contains(21, 0, 0), "next block excluded");
        no(police.contains(0, 320, 0), "height respected");

        Random random = new Random(82731);
        List<ZoneDefinition> zones = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            int x = random.nextInt(4000) - 2000, z = random.nextInt(4000) - 2000;
            zones.add(zone("z" + i, x, z, x + random.nextInt(200), z + random.nextInt(200), i));
        }
        ZoneDefinition whole = zone("whole", -30_000_000, -30_000_000, 30_000_000, 30_000_000, 0);
        zones.add(whole);
        ZoneDefinition nether = zone("nether", -10, -10, 10, 10, 0); nether.dimension = "minecraft:the_nether"; zones.add(nether);
        ZoneIndex index = new ZoneIndex(zones);
        for (int i = 0; i < 3000; i++) {
            int x = random.nextInt(5000) - 2500, z = random.nextInt(5000) - 2500;
            Set<ZoneDefinition> expected = new HashSet<>();
            for (ZoneDefinition region : zones) if (region.dimension.equals("minecraft:overworld") && region.contains(x, 64, z)) expected.add(region);
            yes(expected.equals(new HashSet<>(index.at("minecraft:overworld", x, 64, z))), "index matches linear scan " + i);
        }
        yes(index.at("minecraft:the_nether", 0, 64, 0).equals(List.of(nether)), "dimension isolation");
        yes(index.at("other:world", 0, 64, 0).isEmpty(), "unknown world");
        yes(new ZoneIndex(List.of()).at("minecraft:overworld", 0, 0, 0).isEmpty(), "empty index");
        System.out.println("ZonePolicyTest: " + assertions + " assertions passed");
    }
    private static ZoneDefinition zone(String id, int minX, int minZ, int maxX, int maxZ, int priority) {
        ZoneDefinition z = new ZoneDefinition(); z.id = id; z.dimension = "minecraft:overworld";
        z.minX = minX; z.minY = -64; z.minZ = minZ; z.maxX = maxX; z.maxY = 319; z.maxZ = maxZ; z.priority = priority;
        return z;
    }
    private static boolean allowed(List<ZoneDefinition> zones, String player, Set<String> factions) {
        return ZoneIndex.permits(zones, ZoneAction.DOORS, player, factions, true);
    }
    private static void yes(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void no(boolean value, String message) { yes(!value, message); }
}
