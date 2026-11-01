package ru.mosvegas.casino.net;

/** Client -> server requests from the table menu. Every one is validated on the server. */
public enum TableAction {
    /** arg = -1 main bet, otherwise SideBet ordinal; amount = chip value. */
    BET,
    UNDO,
    CLEAR,
    REBET,
    DOUBLE_BETS,
    READY,
    /** arg = Decision ordinal. */
    DECIDE,
    /** arg = 1 take, 0 decline. */
    INSURANCE,
    LEAVE,
    /** arg = box. */
    SIT,
    /** arg = box. */
    BEHIND,
    SYNC;

    public static TableAction byId(int id) {
        TableAction[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
