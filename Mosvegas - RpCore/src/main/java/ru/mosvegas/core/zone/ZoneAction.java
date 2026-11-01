package ru.mosvegas.core.zone;

import java.util.Locale;

/** Stable names used by commands, JSON and integration mods. Movement is deliberately absent. */
public enum ZoneAction {
    BUILD("строительство и разрушение"), DOORS("открытие дверей"), CONTAINERS("доступ к контейнерам"),
    INTERACT("взаимодействие с блоками"), ENTITIES("взаимодействие с существами"), PVP("бой между игроками"),
    SHOOT("стрельба"), TERRAIN("изменение территории"), VEHICLE_ENTER("посадка в транспорт"),
    VEHICLE_USE("управление транспортом"), VEHICLE_PLACE("создание транспорта"), VEHICLE_DAMAGE("повреждение транспорта");

    private final String description;
    ZoneAction(String description) { this.description = description; }
    public String description() { return description; }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static ZoneAction parse(String value) { return valueOf(value.toUpperCase(Locale.ROOT)); }
}
