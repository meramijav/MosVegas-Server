package studio.merami.slimcore.plates;

public record PlatePrices(long full, long letters, long digits, long letter, long digit, long region, boolean firstFree) {
    public static final PlatePrices DEFAULT = new PlatePrices(5000, 3000, 3000, 1500, 1500, 2000, false);

    public long price(PlateNumber.Spin spin, boolean numbered) {
        if (spin == PlateNumber.Spin.FULL && !numbered && firstFree) return 0;
        return switch (spin) {
            case FULL -> full;
            case LETTERS -> letters;
            case DIGITS -> digits;
            case LETTER -> letter;
            case DIGIT -> digit;
            case REGION -> region;
        };
    }
}
