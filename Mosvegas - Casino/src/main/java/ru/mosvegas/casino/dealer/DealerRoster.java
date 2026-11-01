package ru.mosvegas.casino.dealer;

import ru.mosvegas.casino.config.CasinoConfig;
import ru.mosvegas.casino.config.ConfigManager;
import ru.mosvegas.casino.config.DealerProfile;
import ru.mosvegas.casino.config.TableStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Who works where. A dealer is at one table at a time and rests {@code breakMinutes} after a shift
 * ("по кд"), so tables rotate through all configured croupiers.
 */
public final class DealerRoster {
    private static final Map<String, String> busy = new HashMap<>();
    private static final Map<String, Long> breakUntil = new HashMap<>();
    private static final Random random = new Random();

    private DealerRoster() {}

    public static synchronized DealerProfile acquire(String tableKey, TableStyle style, String avoid) {
        CasinoConfig config = ConfigManager.get();
        long now = System.currentTimeMillis();
        List<DealerProfile> free = new ArrayList<>();
        for (DealerProfile p : config.dealers.profiles) {
            if (!p.enabled || busy.containsKey(p.id)) continue;
            if (breakUntil.getOrDefault(p.id, 0L) > now) continue;
            if (style != null && !style.dealers.isEmpty() && !style.dealers.contains(p.id)) continue;
            if (style != null && !p.styles.isEmpty() && !p.styles.contains(style.id)) continue;
            free.add(p);
        }
        if (free.size() > 1 && avoid != null) free.removeIf(p -> p.id.equals(avoid));
        if (free.isEmpty()) return null;
        DealerProfile chosen = free.get(random.nextInt(free.size()));
        busy.put(chosen.id, tableKey);
        return chosen;
    }

    public static synchronized void release(String id, long breakMillis) {
        if (id == null) return;
        busy.remove(id);
        if (breakMillis > 0) breakUntil.put(id, System.currentTimeMillis() + breakMillis);
    }

    public static synchronized void reset() {
        busy.clear();
        breakUntil.clear();
    }

    /** Human readable status for {@code /casino dealer list}. */
    public static synchronized List<String> status() {
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (DealerProfile p : ConfigManager.get().dealers.profiles) {
            String state;
            if (!p.enabled) state = "§8выключен";
            else if (busy.containsKey(p.id)) state = "§aза столом " + busy.get(p.id);
            else if (breakUntil.getOrDefault(p.id, 0L) > now) state = "§eперерыв ещё " + (breakUntil.get(p.id) - now) / 1000 + " с";
            else state = "§7свободен";
            lines.add("§f" + p.name + " §7(" + p.id + ", " + (p.slim() ? "ж" : "м") + ") — " + state);
        }
        return lines;
    }
}
