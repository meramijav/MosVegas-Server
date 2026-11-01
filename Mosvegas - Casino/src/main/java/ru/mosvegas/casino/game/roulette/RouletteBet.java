package ru.mosvegas.casino.game.roulette;

import java.util.Arrays;
import java.util.UUID;

/**
 * One chip stack on the layout. Inside bets list their numbers; outside bets use {@code parameter}
 * (dozen 0..2, column 0..2) and an empty number list.
 */
public record RouletteBet(UUID player, RouletteBetType type, int[] numbers, int parameter, long amount) {
    public RouletteBet {
        if (type.inside() && (numbers == null || numbers.length != type.numbers)) {
            throw new IllegalArgumentException(type + " needs " + type.numbers + " numbers");
        }
        numbers = numbers == null ? new int[0] : numbers.clone();
    }

    public boolean wins(int number) {
        if (type.inside()) return Arrays.stream(numbers).anyMatch(n -> n == number);
        return type.outsideWins(number, parameter);
    }

    /** Total returned to the player (stake + winnings), 0 on a loss. */
    public long payout(int number) {
        return wins(number) ? amount * (type.payout + 1) : 0;
    }

    public static RouletteBet straight(UUID player, int number, long amount) {
        return new RouletteBet(player, RouletteBetType.STRAIGHT, new int[]{number}, 0, amount);
    }

    public static RouletteBet outside(UUID player, RouletteBetType type, int parameter, long amount) {
        return new RouletteBet(player, type, null, parameter, amount);
    }
}
