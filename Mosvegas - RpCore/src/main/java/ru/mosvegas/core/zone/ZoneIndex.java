package ru.mosvegas.core.zone;

import java.util.*;

/** Immutable bounding-volume tree: a city-sized region does not allocate millions of chunk entries. */
public final class ZoneIndex {
    private final Map<String, Node> worlds = new HashMap<>();

    public ZoneIndex(Collection<ZoneDefinition> zones) {
        Map<String, List<ZoneDefinition>> grouped = new HashMap<>();
        for (ZoneDefinition zone : zones) grouped.computeIfAbsent(zone.dimension, k -> new ArrayList<>()).add(zone);
        grouped.forEach((world, list) -> worlds.put(world, new Node(list, 0)));
    }

    public List<ZoneDefinition> at(String world, double x, double y, double z) {
        List<ZoneDefinition> result = new ArrayList<>();
        Node root = worlds.get(world);
        if (root != null) root.collect(x, y, z, result);
        return result;
    }

    public static boolean permits(List<ZoneDefinition> zones, ZoneAction action, String uuid,
                                  Set<String> factions, boolean fallback) {
        int priority = Integer.MIN_VALUE;
        boolean found = false, allowed = true;
        for (ZoneDefinition zone : zones) {
            if (!zone.flags.containsKey(action) || (found && zone.priority < priority)) continue;
            boolean decision = zone.permits(action, uuid, factions);
            if (!found || zone.priority > priority) {
                priority = zone.priority;
                allowed = decision;
                found = true;
            } else allowed &= decision; // An equal-priority allow cannot defeat a restriction.
        }
        return found ? allowed : fallback;
    }

    private static final class Node {
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final Node left, right;
        final List<ZoneDefinition> leaves;

        Node(List<ZoneDefinition> zones, int depth) {
            minX = zones.stream().mapToInt(z -> z.minX).min().orElseThrow();
            minY = zones.stream().mapToInt(z -> z.minY).min().orElseThrow();
            minZ = zones.stream().mapToInt(z -> z.minZ).min().orElseThrow();
            maxX = zones.stream().mapToInt(z -> z.maxX).max().orElseThrow();
            maxY = zones.stream().mapToInt(z -> z.maxY).max().orElseThrow();
            maxZ = zones.stream().mapToInt(z -> z.maxZ).max().orElseThrow();
            if (zones.size() <= 8) {
                leaves = List.copyOf(zones);
                left = right = null;
            } else {
                zones.sort(Comparator.comparingDouble(z -> depth % 2 == 0
                        ? (double) z.minX + z.maxX : (double) z.minZ + z.maxZ));
                int middle = zones.size() / 2;
                left = new Node(new ArrayList<>(zones.subList(0, middle)), depth + 1);
                right = new Node(new ArrayList<>(zones.subList(middle, zones.size())), depth + 1);
                leaves = null;
            }
        }

        void collect(double x, double y, double z, List<ZoneDefinition> result) {
            if (x < minX || x >= (double) maxX + 1 || y < minY || y >= (double) maxY + 1
                    || z < minZ || z >= (double) maxZ + 1) return;
            if (leaves != null) {
                for (ZoneDefinition zone : leaves) if (zone.contains(x, y, z)) result.add(zone);
            } else {
                left.collect(x, y, z, result);
                right.collect(x, y, z, result);
            }
        }
    }
}
