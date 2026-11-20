package studio.merami.slimcore.plates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class PlateNumber {
    public enum Spin {
        FULL,
        LETTERS,
        DIGITS,
        LETTER,
        DIGIT,
        REGION
    }

    public static final String NONE = "";
    public static final String STANDARD = "standard";
    public static final String POLICE = "police";
    public static final String LETTERS = "ABEKMHOPCTYX";
    public static final String CYRILLIC = "АВЕКМНОРСТУХ";
    public static final String DIGITS = "0123456789";
    private static final Pattern STANDARD_FORMAT = Pattern.compile("[" + LETTERS + "][0-9]{3}[" + LETTERS + "]{2}[0-9]{2,3}");
    private static final Pattern POLICE_FORMAT = Pattern.compile("[" + LETTERS + "][0-9]{4}[0-9]{2,3}");
    private static final int STANDARD_BODY = 6;
    private static final int POLICE_BODY = 5;
    private static final int ATTEMPTS = 4096;

    private PlateNumber() {}

    public static boolean valid(String number) {
        return number != null && (STANDARD_FORMAT.matcher(number).matches() || POLICE_FORMAT.matcher(number).matches()) && PlateRegions.known(region(number));
    }

    public static String style(String number) {
        return Character.isDigit(number.charAt(4)) ? POLICE : STANDARD;
    }

    public static int bodyLength(String style) {
        return POLICE.equals(style) ? POLICE_BODY : STANDARD_BODY;
    }

    public static boolean letterSlot(String style, int slot) {
        return POLICE.equals(style) ? slot == 0 : slot == 0 || slot == 4 || slot == 5;
    }

    public static String body(String number) {
        return number.substring(0, bodyLength(style(number)));
    }

    public static String region(String number) {
        return number.substring(bodyLength(style(number)));
    }

    public static String display(String number) {
        if (!valid(number)) return "";
        return cyrillic(body(number)) + " " + region(number);
    }

    public static String cyrillic(String latin) {
        StringBuilder text = new StringBuilder(latin.length());
        for (char character : latin.toCharArray()) {
            int index = LETTERS.indexOf(character);
            text.append(index < 0 ? character : CYRILLIC.charAt(index));
        }
        return text.toString();
    }

    public static String spin(String current, String style, String region, Spin spin, Random random, Predicate<String> taken) {
        return spin(current, style, region, spin, -1, random, taken);
    }

    public static String spin(String current, String style, String region, Spin spin, int slot, Random random, Predicate<String> taken) {
        if (!PlateRegions.known(region)) throw new IllegalArgumentException("Unknown region " + region);
        boolean same = valid(current) && style(current).equals(style);
        if (spin != Spin.FULL && !same) throw new IllegalArgumentException("Only a full spin gives a car without a number its first one");
        String body = same ? body(current) : blank(style);
        if (spin == Spin.REGION) return otherRegion(body, region(current), random, taken);
        if (spin == Spin.LETTER || spin == Spin.DIGIT) {
            if (!slotFits(style, spin, slot)) throw new IllegalArgumentException("Slot " + slot + " of a " + style + " number does not hold a " + (spin == Spin.LETTER ? "letter" : "digit"));
            return symbol(current, body, style, region, slot, random, taken);
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String next = roll(body, style, spin, random) + region;
            if (!next.equals(current) && !taken.test(next)) return next;
        }
        return null;
    }

    public static boolean slotFits(String style, Spin spin, int slot) {
        return slot >= 0 && slot < bodyLength(style) && letterSlot(style, slot) == (spin == Spin.LETTER);
    }

    private static String otherRegion(String body, String current, Random random, Predicate<String> taken) {
        List<String> codes = new ArrayList<>(PlateRegions.ALL);
        codes.remove(current);
        Collections.shuffle(codes, random);
        for (String code : codes) {
            if (!taken.test(body + code)) return body + code;
        }
        return null;
    }

    private static String symbol(String current, String body, String style, String region, int slot, Random random, Predicate<String> taken) {
        String set = letterSlot(style, slot) ? LETTERS : DIGITS;
        List<Character> options = new ArrayList<>();
        for (char character : set.toCharArray()) {
            if (character != body.charAt(slot)) options.add(character);
        }
        Collections.shuffle(options, random);
        for (char character : options) {
            char[] chars = body.toCharArray();
            chars[slot] = character;
            if (zeroDigits(chars, style)) continue;
            String next = new String(chars) + region;
            if (!next.equals(current) && !taken.test(next)) return next;
        }
        return null;
    }

    private static String blank(String style) {
        return POLICE.equals(style) ? "A0001" : "A001AA";
    }

    private static String roll(String body, String style, Spin spin, Random random) {
        char[] chars = body.toCharArray();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            for (int slot = 0; slot < chars.length; slot++) {
                boolean letter = letterSlot(style, slot);
                if (letter && spin != Spin.DIGITS) chars[slot] = LETTERS.charAt(random.nextInt(LETTERS.length()));
                if (!letter && spin != Spin.LETTERS) chars[slot] = DIGITS.charAt(random.nextInt(DIGITS.length()));
            }
            if (!zeroDigits(chars, style)) break;
        }
        return new String(chars);
    }

    private static boolean zeroDigits(char[] chars, String style) {
        for (int slot = 0; slot < chars.length; slot++) {
            if (!letterSlot(style, slot) && chars[slot] != '0') return false;
        }
        return true;
    }
}
