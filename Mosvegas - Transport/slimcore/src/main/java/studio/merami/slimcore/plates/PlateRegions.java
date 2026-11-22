package studio.merami.slimcore.plates;

import java.util.List;
import java.util.stream.IntStream;

public final class PlateRegions {
    public static final String DEFAULT = "77";
    public static final int DIGITS = 2;
    public static final List<String> MOSCOW = List.of("77", "97", "99", "177", "197", "199", "777", "797", "799", "977");
    public static final List<String> OBLAST = List.of("50", "90", "150", "190", "750", "790");
    public static final List<String> FIRST = List.of("77", "97", "99", "50", "90");
    public static final List<String> CODES = IntStream.rangeClosed(1, 99).mapToObj(code -> String.format("%02d", code)).toList();

    private PlateRegions() {}

    public static boolean known(String code) {
        return CODES.contains(code) || MOSCOW.contains(code) || OBLAST.contains(code);
    }

    public static String twoDigit(String code) {
        return code.length() > DIGITS ? code.substring(code.length() - DIGITS) : code;
    }
}
