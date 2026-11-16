package ru.mosvegas.items.rp;

public enum RpKind {
    POLICE_ID("police_id", 1, Action.SHOW, "animation.police_id.show"),
    PROSECUTOR_ID("prosecutor_id", 1, Action.SHOW, "animation.prosecutor_id.show"),
    FSB_ID("fsb_id", 1, Action.SHOW, "animation.fsb_id.show"),
    MILITARY_ID("military_id", 1, Action.SHOW, "animation.military_id.show"),
    PASSPORT("passport", 1, Action.SHOW, "animation.passport.show"),
    BANK_CARD("bank_card", 1, Action.SHOW, "animation.bank_card.count"),
    CASH_500("cash_500", 64, Action.SHOW, "animation.cash.count"),
    CASH_1000("cash_1000", 64, Action.SHOW, "animation.cash.count"),
    CASH_5000("cash_5000", 64, Action.SHOW, "animation.cash.count"),
    KNUCKLES("knuckles", 1, Action.WEAPON, null),
    RADIO("radio", 1, Action.SELF, "animation.radio.use_3p"),
    HANDCUFFS("handcuffs", 4, Action.TARGET, "animation.handcuffs.use", -9.3f, 0, Effect.CUFF),
    ROPE("rope", 16, Action.TARGET, "animation.rope.use", -16, 180, Effect.TIE),
    STUN_GUN("stun_gun", 1, Action.TARGET, "animation.stun_gun.use_3p", -16, 180, Effect.STUN),
    BANDAGE("bandage", 16, Action.SELF, "animation.bandage.use_3p", Effect.HEAL_SMALL),
    PILLS("pills", 10, Action.SELF, "animation.pills.use_3p", Effect.HEAL_PILLS),
    MEDKIT("medkit", 1, Action.SELF, "animation.medkit.preview_3p", Effect.HEAL_FULL),
    DEFIBRILLATOR("defibrillator", 1, Action.TARGET, "animation.defibrillator.use_3p", 0, 0, Effect.REVIVE);

    public enum Action { SHOW, SELF, TARGET, WEAPON }

    public enum Effect { NONE, CUFF, TIE, STUN, HEAL_SMALL, HEAL_PILLS, HEAL_FULL, REVIVE }

    public final String id;
    public final int stackSize;
    public final Action action;
    public final String animation;
    public final float offsetZ;
    public final float targetYaw;
    public final Effect effect;

    RpKind(String id, int stackSize, Action action, String animation) {
        this(id, stackSize, action, animation, 0, 0, Effect.NONE);
    }

    RpKind(String id, int stackSize, Action action, String animation, Effect effect) {
        this(id, stackSize, action, animation, 0, 0, effect);
    }

    RpKind(String id, int stackSize, Action action, String animation, float offsetZ, float targetYaw, Effect effect) {
        this.id = id;
        this.stackSize = stackSize;
        this.action = action;
        this.animation = animation;
        this.offsetZ = offsetZ;
        this.targetYaw = targetYaw;
        this.effect = effect;
    }

    public String scene() {
        return id.startsWith("cash_") ? "cash" : id;
    }

    public static RpKind byId(String id) {
        for (RpKind kind : values()) if (kind.id.equals(id)) return kind;
        return null;
    }
}
