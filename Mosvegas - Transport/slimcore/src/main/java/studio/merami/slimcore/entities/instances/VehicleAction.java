package studio.merami.slimcore.entities.instances;

public enum VehicleAction {
    ENGINE,
    GEAR_UP,
    GEAR_DOWN,
    LIGHTS,
    HORN,
    LOCK,
    SIREN,
    RADIO,
    WINDOW,
    MEGAPHONE,
    WIPERS,
    EXIT,
    INTERACT,
    ENTER,
    RADIO_NEXT,
    RADIO_PREVIOUS;

    private static final VehicleAction[] VALUES = values();

    public static VehicleAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
