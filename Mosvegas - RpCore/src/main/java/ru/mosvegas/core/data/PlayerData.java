package ru.mosvegas.core.data;

import java.util.*;

public final class PlayerData {
    public long bankBalance;
    /** Legacy value migrated to physical banknote items on the next login. */
    public long cash;
    public boolean initialized;
    public Passport passport;
    public Set<String> licences = new LinkedHashSet<>();
    public String faction;
    public int factionLevel;
    public int hierarchyLevel;
    public int factionXp;
    public int serverLevel;
    public long serverLevelTicks;
    public long lastSalaryAt;
    public List<ActiveQuest> activeQuests = new ArrayList<>();
    /** Starter quests are one-time and remain completed across faction changes. */
    public Set<String> completedStarterQuests = new LinkedHashSet<>();
    /** Faction quests are one-time during the current faction membership only. */
    public Set<String> completedFactionQuests = new LinkedHashSet<>();

    public static final class Passport {
        public String firstName;
        public String lastName;
        public String gender;
        public int age;
        public long issuedAt;
    }

    public static final class ActiveQuest {
        public String templateId;
        public int pointIndex;
        public int progressTicks;
        public String chosenItem;
        public long acceptedAt;
    }
}
