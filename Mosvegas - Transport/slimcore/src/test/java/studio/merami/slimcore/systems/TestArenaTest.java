package studio.merami.slimcore.systems;

import org.junit.jupiter.api.Test;
import studio.merami.slimcore.TestPacks;
import studio.merami.slimcore.baseclasses.BlockPosition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestArenaTest {
    private static final BlockPosition ORIGIN = new BlockPosition(100, -60, -40);

    @Test
    void everyFillStaysInsideTheBounds() {
        TestArena.Fill bounds = TestArena.bounds(ORIGIN);
        List<TestArena.Fill> fills = TestArena.layout(ORIGIN);
        assertTrue(fills.size() > 50);
        for (TestArena.Fill fill : fills) {
            assertTrue(bounds.contains(fill.minX(), fill.minY(), fill.minZ()) && bounds.contains(fill.maxX(), fill.maxY(), fill.maxZ()), fill.toString());
        }
    }

    @Test
    void rampStepsAreLowEnoughForTheJetski() {
        double maxStep = TestPacks.vehicle("jetski").maxStep;
        assertTrue(maxStep >= .5, "half block ramp steps need max_step .5, have " + maxStep);
        double draft = TestPacks.vehicle("jetski").craft.draft;
        assertTrue(1 / 9.0 + draft <= maxStep, "the first ramp step sits just above the water line");
    }

    @Test
    void pierIsAboveTheWater() {
        assertTrue(TestArena.pier(ORIGIN).y >= ORIGIN.y() + TestArena.WATER_DEPTH);
    }
}
