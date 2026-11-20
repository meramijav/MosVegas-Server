package studio.merami.sliminterfacefabric.client.ui;

public final class GarageUi {
    public record Area(int x, int y, int w, int h) {
        public boolean inside(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }
    }

    private GarageUi() {}

    public static int mix(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int from = a >>> shift & 0xFF;
            int to = b >>> shift & 0xFF;
            result |= Math.round(from + (to - from) * t) << shift;
        }
        return result;
    }

    public static String number(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder text = new StringBuilder(value < 0 ? "-" : "");
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) text.append(' ');
            text.append(digits.charAt(i));
        }
        return text.toString();
    }
}
