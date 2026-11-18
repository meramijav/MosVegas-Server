package studio.merami.slimcore.plates;

import java.util.List;

public final class PlateRegions {
    public static final String DEFAULT = "77";
    public static final List<String> MOSCOW = List.of("77", "97", "99", "177", "197", "199", "777", "797", "799", "977");
    public static final List<String> OBLAST = List.of("50", "90", "150", "190", "750", "790");

    private PlateRegions() {}

    public static boolean known(String code) {
        return MOSCOW.contains(code) || OBLAST.contains(code);
    }
}
