package ru.mosvegas.casino.table;

import net.minecraft.util.StringIdentifiable;

/**
 * Footprint of a semicircular table. The model, collision and every seat/card position derive from
 * {@link #radius}: MEDIUM is 3x2 blocks (3-5 boxes), LARGE is 5x3 blocks (5-7 boxes).
 */
public enum TableSize implements StringIdentifiable {
    MEDIUM("medium", 1.45, 1, 2),
    LARGE("large", 2.4, 2, 3);

    public final String id;
    public final double radius;
    public final int halfWidth;
    public final int depth;

    TableSize(String id, double radius, int halfWidth, int depth) {
        this.id = id;
        this.radius = radius;
        this.halfWidth = halfWidth;
        this.depth = depth;
    }

    @Override public String asString() { return id; }

    public static TableSize parse(String id, TableSize fallback) {
        if (id == null) return fallback;
        for (TableSize s : values()) if (s.id.equalsIgnoreCase(id)) return s;
        return fallback;
    }
}
