package studio.merami.slimcore.plates;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlateNumberTest {
    private static final String CAR = "A123BC77";
    private static final String POLICE_CAR = "A123477";

    @Test
    void onlyRussianNumbersOfKnownRegionsAreValid() {
        assertTrue(PlateNumber.valid(CAR));
        assertTrue(PlateNumber.valid("X999YX777"));
        assertTrue(PlateNumber.valid(POLICE_CAR));
        assertTrue(PlateNumber.valid("A123BC05"));
        assertFalse(PlateNumber.valid("A123BC00"));
        assertFalse(PlateNumber.valid("A123BC555"));
        assertFalse(PlateNumber.valid("A000BC77"));
        assertFalse(PlateNumber.valid("A000077"));
        assertFalse(PlateNumber.valid("D123BC77"));
        assertFalse(PlateNumber.valid("AB-123-CD"));
        assertFalse(PlateNumber.valid("a123bc77"));
        assertFalse(PlateNumber.valid(PlateNumber.NONE));
        assertFalse(PlateNumber.valid(null));
    }

    @Test
    void numbersSplitIntoBodyAndRegion() {
        assertEquals("A123BC", PlateNumber.body(CAR));
        assertEquals("77", PlateNumber.region(CAR));
        assertEquals("777", PlateNumber.region("A123BC777"));
        assertEquals("A1234", PlateNumber.body(POLICE_CAR));
        assertEquals("77", PlateNumber.region(POLICE_CAR));
        assertEquals("А123ВС 77", PlateNumber.display(CAR));
    }

    @Test
    void aFullSpinGivesANewNumberInTheChosenRegion() {
        Random random = new Random(7);
        String first = PlateNumber.spin(PlateNumber.NONE, PlateNumber.STANDARD, "150", PlateNumber.Spin.FULL, random, number -> false);
        assertTrue(PlateNumber.valid(first));
        assertEquals("150", PlateNumber.region(first));
        String next = PlateNumber.spin(first, PlateNumber.STANDARD, "150", PlateNumber.Spin.FULL, random, number -> false);
        assertNotEquals(first, next);
        String police = PlateNumber.spin(PlateNumber.NONE, PlateNumber.POLICE, "77", PlateNumber.Spin.FULL, random, number -> false);
        assertEquals(PlateNumber.POLICE, PlateNumber.style(police));
    }

    @Test
    void lettersAndDigitsSpinOnlyTheirOwnPart() {
        Random random = new Random(11);
        for (int i = 0; i < 200; i++) {
            String letters = PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.LETTERS, random, number -> false);
            assertEquals("123", letters.substring(1, 4));
            assertNotEquals(CAR, letters);
            String digits = PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGITS, random, number -> false);
            assertEquals("A", digits.substring(0, 1));
            assertEquals("BC77", digits.substring(4));
            assertNotEquals(CAR, digits);
        }
    }

    @Test
    void aSpinMovesTheNumberToTheChosenRegion() {
        String moved = PlateNumber.spin(CAR, PlateNumber.STANDARD, "199", PlateNumber.Spin.DIGITS, new Random(5), number -> false);
        assertEquals("199", PlateNumber.region(moved));
    }

    @Test
    void aSingleSymbolSpinChangesOnlyThatSymbol() {
        Random random = new Random(17);
        for (int i = 0; i < 300; i++) {
            String letter = PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.LETTER, 4, random, number -> false);
            assertEquals("A123", letter.substring(0, 4));
            assertNotEquals('B', letter.charAt(4));
            assertEquals("C77", letter.substring(5));
            String digit = PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGIT, 2, random, number -> false);
            assertEquals("A1", digit.substring(0, 2));
            assertNotEquals('2', digit.charAt(2));
            assertEquals("3BC77", digit.substring(3));
            String police = PlateNumber.spin(POLICE_CAR, PlateNumber.POLICE, "77", PlateNumber.Spin.DIGIT, 4, random, number -> false);
            assertEquals("A123", police.substring(0, 4));
            assertNotEquals('4', police.charAt(4));
        }
    }

    @Test
    void aSingleSymbolSpinNeedsASlotOfItsKind() {
        Random random = new Random(1);
        assertThrows(IllegalArgumentException.class, () -> PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.LETTER, 1, random, number -> false));
        assertThrows(IllegalArgumentException.class, () -> PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGIT, 0, random, number -> false));
        assertThrows(IllegalArgumentException.class, () -> PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGIT, 8, random, number -> false));
        assertThrows(IllegalArgumentException.class, () -> PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.LETTER, 6, random, number -> false));
        assertThrows(IllegalArgumentException.class, () -> PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.LETTER, -1, random, number -> false));
    }

    @Test
    void aSingleDigitNeverZeroesTheNumber() {
        Random random = new Random(19);
        for (int i = 0; i < 500; i++) {
            String next = PlateNumber.spin("A001BC77", PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGIT, 3, random, number -> false);
            assertNotEquals("000", next.substring(1, 4));
        }
    }

    @Test
    void aRegionSpinKeepsTheBodyAndPicksAnotherKnownRegion() {
        Random random = new Random(23);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            String next = PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.REGION, random, number -> false);
            assertEquals(2, PlateNumber.region(next).length());
            assertEquals("A123BC", PlateNumber.body(next));
            assertNotEquals("77", PlateNumber.region(next));
            assertTrue(PlateRegions.known(PlateNumber.region(next)));
            seen.add(PlateNumber.region(next));
        }
        assertEquals(PlateRegions.CODES.size() - 1, seen.size());
        assertFalse(seen.contains("00"));
        assertEquals("A123BC50", PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.REGION, random, number -> !number.endsWith("BC50")));
        assertNull(PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.REGION, random, number -> true));
    }

    @Test
    void aRegionDigitSpinChangesOnlyThatDigit() {
        Random random = new Random(29);
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String first = PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGIT, 6, random, number -> false);
            assertEquals("A123BC", PlateNumber.body(first));
            assertNotEquals('7', PlateNumber.region(first).charAt(0));
            assertEquals('7', PlateNumber.region(first).charAt(1));
            seen.add(PlateNumber.region(first).charAt(0));
            String second = PlateNumber.spin(POLICE_CAR, PlateNumber.POLICE, "77", PlateNumber.Spin.DIGIT, 6, random, number -> false);
            assertEquals("A12347", second.substring(0, 6));
            assertNotEquals('7', second.charAt(6));
        }
        assertTrue(seen.contains('0'));
        assertEquals(9, seen.size());
    }

    @Test
    void aRegionNeverBecomesZero() {
        Random random = new Random(31);
        for (int i = 0; i < 500; i++) {
            String next = PlateNumber.spin("A123BC07", PlateNumber.STANDARD, "07", PlateNumber.Spin.DIGIT, 7, random, number -> false);
            assertNotEquals("00", PlateNumber.region(next));
        }
    }

    @Test
    void aThreeDigitRegionDropsToItsLastTwoDigits() {
        Random random = new Random(37);
        for (int i = 0; i < 200; i++) {
            String next = PlateNumber.spin("A123BC177", PlateNumber.STANDARD, "177", PlateNumber.Spin.DIGIT, 6, random, number -> false);
            assertEquals(2, PlateNumber.region(next).length());
            assertEquals('7', PlateNumber.region(next).charAt(1));
            assertNotEquals('7', PlateNumber.region(next).charAt(0));
            String region = PlateNumber.region(PlateNumber.spin("A123BC177", PlateNumber.STANDARD, "177", PlateNumber.Spin.REGION, random, number -> false));
            assertEquals(2, region.length());
        }
    }

    @Test
    void digitsAreNeverAllZero() {
        Random random = new Random(13);
        for (int i = 0; i < 5000; i++) {
            String next = PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGITS, random, number -> false);
            assertNotEquals("000", next.substring(1, 4));
        }
    }

    @Test
    void aTakenNumberIsNeverGiven() {
        Random random = new Random(3);
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 900; i++) {
            String next = PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGITS, random, taken::contains);
            if (next == null) break;
            assertTrue(taken.add(next));
        }
        assertFalse(taken.contains(CAR));
    }

    @Test
    void exhaustedNumbersGiveNothing() {
        assertNull(PlateNumber.spin(CAR, PlateNumber.STANDARD, "77", PlateNumber.Spin.DIGITS, new Random(1), number -> true));
    }

    @Test
    void partSpinsNeedANumber() {
        assertThrows(IllegalArgumentException.class, () -> PlateNumber.spin(PlateNumber.NONE, PlateNumber.STANDARD, "77", PlateNumber.Spin.LETTERS, new Random(1), number -> false));
        assertThrows(IllegalArgumentException.class, () -> PlateNumber.spin(CAR, PlateNumber.STANDARD, "00", PlateNumber.Spin.FULL, new Random(1), number -> false));
    }
}
