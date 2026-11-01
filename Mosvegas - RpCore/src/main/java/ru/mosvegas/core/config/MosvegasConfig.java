package ru.mosvegas.core.config;

import java.util.*;

public final class MosvegasConfig {
    public int schemaVersion;
    public String currency = "₽";
    public long startingBankBalance = 5000;
    public int salaryIntervalSeconds = 1800;
    public int taxPercent = 13;
    public int maxActiveQuests = 3;
    public int serverLevelIntervalSeconds = 3600;
    public List<Long> cashDenominations = new ArrayList<>(List.of(5000L, 2000L, 1000L, 500L, 200L, 100L, 50L, 10L, 5L, 2L, 1L));
    public Map<String, Long> salaryByFactionLevel = new LinkedHashMap<>();
    public Map<String, Long> licencePrices = new LinkedHashMap<>();
    public List<Faction> factions = new ArrayList<>();
    public List<Quest> quests = new ArrayList<>();
    public List<Property> properties = new ArrayList<>();
    public List<Point> bankTerminals = new ArrayList<>();

    public static MosvegasConfig defaults() {
        MosvegasConfig c = new MosvegasConfig();
        c.schemaVersion = 4;
        c.salaryByFactionLevel.put("0", 1000L);
        c.salaryByFactionLevel.put("1", 1500L);
        c.salaryByFactionLevel.put("2", 2200L);
        c.salaryByFactionLevel.put("3", 3200L);
        c.salaryByFactionLevel.put("4", 4500L);
        c.salaryByFactionLevel.put("5", 6500L);
        c.licencePrices.put("car", 2500L);
        c.licencePrices.put("weapon", 10000L);
        c.bankTerminals.add(new Point("minecraft:overworld", 0, 64, 1, 0));

        Faction police = new Faction("police", "Полиция", "Охрана порядка", "mosvegas.faction.police");
        police.uniqueItems.add("minecraft:iron_sword");
        Faction military = new Faction("military", "Военные", "Вооружённые силы", "mosvegas.faction.military");
        military.uniqueItems.add("minecraft:crossbow");
        Faction deputy = new Faction("deputy", "Депутаты", "Государственная служба", "mosvegas.faction.deputy");
        deputy.uniqueItems.add("minecraft:written_book");
        c.factions.add(police);
        c.factions.add(military);
        c.factions.add(deputy);

        Quest travel = new Quest("police_patrol", "Патруль района", "Доберитесь до контрольной точки", "TRAVEL", "police");
        travel.reward = 800;
        travel.points.add(new Point("minecraft:overworld", 0, 64, 0, 5));
        travel.points.add(new Point("minecraft:overworld", 100, 64, 100, 5));
        c.quests.add(travel);

        Quest route = new Quest("military_route", "Марш-бросок", "Пройдите точки по порядку", "ROUTE", "military");
        route.reward = 1200;
        route.points.add(new Point("minecraft:overworld", 10, 64, 10, 5));
        route.points.add(new Point("minecraft:overworld", 40, 64, 40, 5));
        route.points.add(new Point("minecraft:overworld", 80, 64, 20, 5));
        c.quests.add(route);

        Quest stay = new Quest("deputy_meeting", "Заседание", "Оставайтесь в зоне", "STAY", "deputy");
        stay.reward = 900;
        stay.staySeconds = 60;
        stay.points.add(new Point("minecraft:overworld", 0, 64, 0, 8));
        c.quests.add(stay);

        Quest find = new Quest("find_evidence", "Найти улику", "Найдите скрытый предмет", "FIND_ITEM", "police");
        find.reward = 1000;
        find.randomItems.add("minecraft:paper");
        find.randomItems.add("minecraft:book");
        find.points.add(new Point("minecraft:overworld", 25, 64, 25, 3));
        c.quests.add(find);

        Quest npc = new Quest("supply_npc", "Передать припасы", "Передайте предмет снабженцу", "NPC_ITEM", "military");
        npc.reward = 1100;
        npc.npcName = "Снабженец";
        npc.npcMode = "GIVE";
        npc.randomItems.add("minecraft:bread");
        npc.randomItems.add("minecraft:iron_ingot");
        c.quests.add(npc);

        Quest takeNpc = new Quest("deputy_documents", "Получить документы", "Заберите случайный документ у секретаря", "NPC_ITEM", "deputy");
        takeNpc.reward = 700;
        takeNpc.npcName = "Секретарь";
        takeNpc.npcMode = "TAKE";
        takeNpc.randomItems.add("minecraft:paper");
        takeNpc.randomItems.add("minecraft:book");
        takeNpc.randomItems.add("minecraft:writable_book");
        c.quests.add(takeNpc);

        Quest starter = new Quest("starter_city", "Знакомство с городом", "Доберитесь до городской площади", "TRAVEL", null);
        starter.reward = 500;
        starter.factionXp = 0;
        starter.points.add(new Point("minecraft:overworld", 0, 64, 0, 5));
        c.quests.add(starter);

        c.properties.add(new Property("apt_1", "Квартира №1", "APARTMENT", 50000, 2500,
                new Point("minecraft:overworld", 0, 64, 0, 2)));
        c.properties.add(new Property("parking_1", "Паркинг №1", "PARKING", 12000, 0,
                new Point("minecraft:overworld", 5, 64, 5, 2)));
        return c;
    }

    public static final class Faction {
        public String id;
        public String name;
        public String description;
        public String permission;
        public List<String> ranks = new ArrayList<>(List.of("Стажёр", "Сотрудник", "Специалист", "Заместитель", "Руководитель"));
        public List<String> uniqueItems = new ArrayList<>();
        public Faction() {}
        public Faction(String id, String name, String description, String permission) {
            this.id=id; this.name=name; this.description=description; this.permission=permission;
        }
    }

    public static final class Quest {
        public String id;
        public String name;
        public String description;
        public String type;
        public String faction;
        public boolean enabled = true;
        public long reward = 500;
        public int factionXp = 1;
        public int staySeconds = 30;
        /** Exact visible entity name used to offer this quest on right click. Empty means menu/command only. */
        public String giverNpcName = "";
        public String npcName = "Квестовый NPC";
        public String npcMode = "GIVE";
        public List<Point> points = new ArrayList<>();
        public List<String> randomItems = new ArrayList<>();
        public Quest() {}
        public Quest(String id, String name, String description, String type, String faction) {
            this.id=id; this.name=name; this.description=description; this.type=type; this.faction=faction;
        }
    }

    public static final class Property {
        public String id;
        public String name;
        public String type;
        public long price;
        public long rentPrice;
        public Point location;
        /** Legacy single box; ConfigManager moves it into {@link #areas} on load. */
        public Point min;
        public Point max;
        /** Protected and visibly outlined volume: a union of block boxes, so an object may be L- or П-shaped. If empty, location/radius is used. */
        public List<Area> areas = new ArrayList<>();
        public boolean protectBlocks = true;
        public Property() {}
        public Property(String id, String name, String type, long price, long rentPrice, Point location) {
            this.id=id; this.name=name; this.type=type; this.price=price; this.rentPrice=rentPrice; this.location=location;
        }
    }

    /** Inclusive block box in the dimension of its property's location. */
    public static final class Area {
        public int minX, minY, minZ, maxX, maxY, maxZ;
        public Area() {}
        public Area(int x1, int y1, int z1, int x2, int y2, int z2) {
            minX=Math.min(x1,x2); minY=Math.min(y1,y2); minZ=Math.min(z1,z2);
            maxX=Math.max(x1,x2); maxY=Math.max(y1,y2); maxZ=Math.max(z1,z2);
        }
        public boolean contains(int x, int y, int z) { return x>=minX && x<=maxX && y>=minY && y<=maxY && z>=minZ && z<=maxZ; }
        public boolean sameAs(Area o) { return minX==o.minX && minY==o.minY && minZ==o.minZ && maxX==o.maxX && maxY==o.maxY && maxZ==o.maxZ; }
        @Override public String toString() { return minX+" "+minY+" "+minZ+" → "+maxX+" "+maxY+" "+maxZ; }
    }

    public static final class Point {
        public String dimension;
        public double x, y, z, radius;
        public Point() {}
        public Point(String dimension, double x, double y, double z, double radius) {
            this.dimension=dimension; this.x=x; this.y=y; this.z=z; this.radius=radius;
        }
    }
}
