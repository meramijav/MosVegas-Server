package ru.mosvegas.casino.config;

import ru.mosvegas.casino.game.blackjack.BlackjackVariant;
import ru.mosvegas.casino.table.TableSize;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tables shipped with models and textures; known to the client for the creative tab. */
public final class BuiltinStyles {
    public record Entry(String id, String name, BlackjackVariant variant, TableSize size, int boxes, long min, long max, long sideMax) {}

    public static final List<Entry> ALL = List.of(
            new Entry("classic_black", "Blackjack Classic", BlackjackVariant.CLASSIC, TableSize.LARGE, 7, 100, 50_000, 5_000),
            new Entry("speed_green", "Speed Blackjack", BlackjackVariant.SPEED, TableSize.LARGE, 7, 100, 25_000, 2_500),
            new Entry("infinite_grey", "Infinite Blackjack", BlackjackVariant.INFINITE, TableSize.LARGE, 7, 50, 10_000, 1_000),
            new Entry("free_bet_purple", "Free Bet Blackjack", BlackjackVariant.FREE_BET, TableSize.MEDIUM, 5, 100, 20_000, 2_000),
            new Entry("salon_prive", "Salon Prive VIP", BlackjackVariant.CLASSIC, TableSize.MEDIUM, 3, 5_000, 1_000_000, 50_000)
    );

    public static final String DEFAULT = "classic_black";

    private BuiltinStyles() {}

    public static Entry find(String id) {
        for (Entry e : ALL) if (e.id.equals(id)) return e;
        return null;
    }

    public static Map<String, TableStyle> defaults() {
        Map<String, TableStyle> map = new LinkedHashMap<>();
        for (Entry e : ALL) map.put(e.id, new TableStyle(e.id, e.name, e.variant, e.size, e.boxes, e.min, e.max, e.sideMax));
        return map;
    }
}
