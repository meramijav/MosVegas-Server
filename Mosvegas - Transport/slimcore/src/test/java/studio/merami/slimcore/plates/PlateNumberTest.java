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
        assertFalse(PlateNumber.valid("A123BC55"));
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
        assertThrows(IllegalArgumentException.class, () -> PlateNumber.spin(CAR, PlateNumber.STANDARD, "55", PlateNumber.Spin.FULL, new Random(1), number -> false));
    }
}
