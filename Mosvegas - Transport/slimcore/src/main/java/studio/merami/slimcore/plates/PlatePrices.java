package studio.merami.slimcore.plates;

public record PlatePrices(long full, long letters, long digits, boolean firstFree) {
    public static final PlatePrices DEFAULT = new PlatePrices(5000, 3000, 3000, false);

    public long price(PlateNumber.Spin spin, boolean numbered) {
        if (spin == PlateNumber.Spin.FULL && !numbered && firstFree) return 0;
        return switch (spin) {
            case FULL -> full;
            case LETTERS -> letters;
            case DIGITS -> digits;
        };
    }
}
