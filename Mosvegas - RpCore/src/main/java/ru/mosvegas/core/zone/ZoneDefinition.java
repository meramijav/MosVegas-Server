package ru.mosvegas.core.zone;

import java.util.*;

public final class ZoneDefinition {
    public enum Rule { ALLOW, DENY, MEMBERS }
    public String id;
    public String dimension;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public int priority;
    public Map<ZoneAction, Rule> flags = new EnumMap<>(ZoneAction.class);
    public Map<ZoneAction, Set<String>> players = new EnumMap<>(ZoneAction.class);
    public Map<ZoneAction, Set<String>> factions = new EnumMap<>(ZoneAction.class);

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < (double) maxX + 1 && y >= minY && y < (double) maxY + 1
                && z >= minZ && z < (double) maxZ + 1;
    }

    public boolean permits(ZoneAction action, String uuid, Set<String> factionIds) {
        Rule rule = flags.get(action);
        if (rule == Rule.ALLOW) return true;
        if (rule != Rule.MEMBERS) return false;
        if (uuid != null && players.getOrDefault(action, Set.of()).contains(uuid)) return true;
        return factions.getOrDefault(action, Set.of()).stream().anyMatch(factionIds::contains);
    }
}
