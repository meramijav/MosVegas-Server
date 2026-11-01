package ru.mosvegas.casino.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.mosvegas.casino.game.blackjack.BlackjackRules;
import ru.mosvegas.casino.game.blackjack.BlackjackVariant;
import ru.mosvegas.casino.table.TableSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A kind of table. The model/texture are looked up by {@link #id} on the client
 * ({@code geo/block/blackjack_table_<id>.geo.json}, {@code textures/block/table/<id>.png}).
 */
public final class TableStyle {
    private static final Gson GSON = new Gson();

    public String id;
    public String name;
    public String variant = BlackjackVariant.CLASSIC.name();
    public String size = TableSize.LARGE.id;
    public int boxes = 7;
    public long minBet = 100;
    public long maxBet = 50_000;
    public long sideMax = 5_000;
    /** Bettors needed before cards are dealt (1 = one player against the dealer). */
    public int minPlayers = 1;
    /** Dealer profile ids allowed at this table; empty = any. */
    public List<String> dealers = new ArrayList<>();
    /** Rule overrides on top of the variant preset (any field of BlackjackRules). */
    public JsonObject rules = new JsonObject();

    private transient BlackjackRules cached;

    public TableStyle() {}

    public TableStyle(String id, String name, BlackjackVariant variant, TableSize size, int boxes, long min, long max, long sideMax) {
        this.id = id;
        this.name = name;
        this.variant = variant.name();
        this.size = size.id;
        this.boxes = boxes;
        this.minBet = min;
        this.maxBet = max;
        this.sideMax = sideMax;
        this.rules = GSON.toJsonTree(BlackjackRules.forVariant(variant)).getAsJsonObject();
    }

    public BlackjackVariant variant() { return BlackjackVariant.parse(variant, BlackjackVariant.CLASSIC); }
    public TableSize size() { return TableSize.parse(size, TableSize.LARGE); }
    public int boxCount() { return Math.max(1, Math.min(size() == TableSize.LARGE ? 7 : 5, boxes)); }

    public BlackjackRules rules() {
        if (cached == null) {
            JsonObject merged = GSON.toJsonTree(BlackjackRules.forVariant(variant())).getAsJsonObject();
            if (rules != null) for (Map.Entry<String, JsonElement> e : rules.entrySet()) merged.add(e.getKey(), e.getValue());
            cached = GSON.fromJson(merged, BlackjackRules.class).sanitize();
        }
        return cached;
    }

    public void invalidate() { cached = null; }
}
