package ru.mosvegas.casino.game.roulette;

/** Bet types of a European roulette layout with their "X to 1" payouts and the count of covered numbers. */
public enum RouletteBetType {
    STRAIGHT(35, 1),
    SPLIT(17, 2),
    STREET(11, 3),
    CORNER(8, 4),
    SIX_LINE(5, 6),
    DOZEN(2, 12),
    COLUMN(2, 12),
    RED(1, 18),
    BLACK(1, 18),
    EVEN(1, 18),
    ODD(1, 18),
    LOW(1, 18),
    HIGH(1, 18);

    public final int payout;
    public final int numbers;

    RouletteBetType(int payout, int numbers) {
        this.payout = payout;
        this.numbers = numbers;
    }

    /** Whether outside bets of this type win on {@code number}; inside bets use their explicit numbers. */
    public boolean outsideWins(int number, int parameter) {
        if (number == 0) return false;
        return switch (this) {
            case DOZEN -> (number - 1) / 12 == parameter;
            case COLUMN -> (number - 1) % 3 == parameter;
            case RED -> RouletteWheel.isRed(number);
            case BLACK -> RouletteWheel.isBlack(number);
            case EVEN -> number % 2 == 0;
            case ODD -> number % 2 == 1;
            case LOW -> number <= 18;
            case HIGH -> number >= 19;
            default -> false;
        };
    }

    public boolean inside() { return ordinal() <= SIX_LINE.ordinal(); }
}
