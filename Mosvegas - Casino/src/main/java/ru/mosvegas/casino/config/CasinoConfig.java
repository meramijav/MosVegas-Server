package ru.mosvegas.casino.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code config/mosvegas-casino.json}. Every field has a default, so a partial file stays valid. */
public final class CasinoConfig {
    public int configVersion = 1;

    public Economy economy = new Economy();
    public Dealers dealers = new Dealers();
    /** Chip denominations offered in the table menu, smallest first. */
    public List<Long> chips = new ArrayList<>(List.of(100L, 500L, 1_000L, 5_000L, 25_000L, 100_000L));
    public Map<String, TableStyle> styles = BuiltinStyles.defaults();
    public Map<String, String> phrases = Phrases.defaults();

    /** Permission node for /casino admin commands (checked through Mosvegas Core when installed). */
    public String adminPermission = "mosvegas.casino.admin";
    public int adminOpLevel = 3;
    /** Players closer than this receive the live table state. */
    public int syncRadius = 48;
    /** Standing players betting behind are removed when they walk further than this from their box. */
    public double behindMaxDistance = 6.0;
    public String chatPrefix = "§6[Казино]§r ";

    public static final class Economy {
        /** auto = Mosvegas Core when installed, otherwise the in-memory dev wallet; rpcore; dev. */
        public String provider = "auto";
        /** bank = bank account of Mosvegas Core; cash = paper money in the inventory; cash_then_bank. */
        public String source = "bank";
        /** Starting money of the dev wallet (only for testing without Mosvegas Core). */
        public long devStartBalance = 100_000;
        public String devCurrency = "₽";
    }

    public static final class Dealers {
        /** Minutes a dealer works before a colleague takes over. */
        public double shiftMinutes = 20;
        /** Cooldown of a dealer after a shift. */
        public double breakMinutes = 5;
        /** The dealer leaves an empty table after this many seconds. */
        public int idleLeaveSeconds = 90;
        /** Keep a dealer at every open table even without players. */
        public boolean alwaysStaffed = false;
        /** If no colleague is free when the shift ends: true = leave anyway (table waits), false = keep working. */
        public boolean leaveWithoutReplacement = false;
        /** Navigation speed multiplier of walking dealers (1.0 = brisk walk). */
        public double walkSpeed = 0.7;
        /** Seconds after which a walking dealer is teleported (blocked path). */
        public int walkTimeoutSeconds = 25;
        public boolean speechBubbles = true;
        /** Radius for dealer phrases in chat, 0 disables chat output. */
        public int chatRadius = 10;
        /** Spoken voice lines (assets/mosvegas_casino/voice/...), heard within this radius; 0 disables the voice. */
        public int voiceRadius = 16;
        /** Small gestures while waiting (squaring the chips, a nod, ...), every this many seconds on average; 0 disables. */
        public int fidgetSeconds = 14;
        public boolean showNameTags = true;
        public String title = "Крупье";
        public List<DealerProfile> profiles = new ArrayList<>(List.of(
                new DealerProfile("anna", "Анна", "female", "mosvegas_casino:textures/entity/dealer/anna.png", 1.04f),
                new DealerProfile("maxim", "Максим", "male", "mosvegas_casino:textures/entity/dealer/maxim.png", 1.0f),
                new DealerProfile("maria", "Мария", "female", "mosvegas_casino:textures/entity/dealer/maria.png", 0.98f),
                new DealerProfile("artem", "Артём", "male", "mosvegas_casino:textures/entity/dealer/artem.png", 1.06f),
                new DealerProfile("elena", "Елена", "female", "mosvegas_casino:textures/entity/dealer/elena.png", 0.94f),
                new DealerProfile("david", "Давид", "male", "mosvegas_casino:textures/entity/dealer/david.png", 0.92f)
        ));
    }

    public TableStyle style(String id) {
        TableStyle s = styles.get(id);
        if (s == null) s = styles.get(BuiltinStyles.DEFAULT);
        if (s == null && !styles.isEmpty()) s = styles.values().iterator().next();
        return s;
    }

    public DealerProfile dealer(String id) {
        for (DealerProfile p : dealers.profiles) if (p.id.equals(id)) return p;
        return null;
    }

    public void normalize() {
        if (economy == null) economy = new Economy();
        if (dealers == null) dealers = new Dealers();
        if (dealers.profiles == null || dealers.profiles.isEmpty()) dealers.profiles = new Dealers().profiles;
        if (chips == null || chips.isEmpty()) chips = new ArrayList<>(List.of(100L, 500L, 1_000L, 5_000L, 25_000L));
        chips.removeIf(v -> v == null || v <= 0);
        chips.sort(Long::compare);
        if (styles == null || styles.isEmpty()) styles = BuiltinStyles.defaults();
        Map<String, TableStyle> fixed = new LinkedHashMap<>();
        for (Map.Entry<String, TableStyle> e : styles.entrySet()) {
            TableStyle s = e.getValue();
            if (s == null) continue;
            s.id = e.getKey();
            if (s.name == null) s.name = s.id;
            if (s.dealers == null) s.dealers = new ArrayList<>();
            s.invalidate();
            fixed.put(s.id, s);
        }
        styles = fixed;
        Map<String, String> phraseDefaults = Phrases.defaults();
        if (phrases == null) phrases = phraseDefaults;
        for (Map.Entry<String, String> e : phraseDefaults.entrySet()) phrases.putIfAbsent(e.getKey(), e.getValue());
        for (DealerProfile p : dealers.profiles) {
            if (p.styles == null) p.styles = new ArrayList<>();
            if (p.name == null) p.name = p.id;
            if (p.model == null) p.model = "";
            if (p.animations == null) p.animations = "";
        }
    }
}
