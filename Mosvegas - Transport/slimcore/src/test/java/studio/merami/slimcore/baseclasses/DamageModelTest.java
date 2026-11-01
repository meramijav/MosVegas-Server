package studio.merami.slimcore.baseclasses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageModelTest {
    @Test
    void engineLosesPowerOnlyWhenBadlyDamaged() {
        assertEquals(1, DamageModel.power(70), "A lightly damaged engine keeps full power");
        assertEquals(1, DamageModel.power(DamageModel.FULL));
        assertTrue(DamageModel.power(20) < .6 && DamageModel.power(0) > 0, "A badly damaged engine keeps some of its power");
    }

    @Test
    void damageGrowsWithTheSquareOfTheSpeedChange() {
        assertEquals(0, DamageModel.impact(.1), "A bump at walking pace does no damage");
        assertTrue(DamageModel.impact(.8) > 30 && DamageModel.impact(.8) < 45, "Damage grows with the square of the speed change: " + DamageModel.impact(.8));
        assertTrue(DamageModel.impact(.6) < DamageModel.impact(.8) * .7, "Less speed, much less damage");
    }
}
