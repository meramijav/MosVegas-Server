package studio.merami.slimcore.plates;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlateRegistryTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAR = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void driverIsRememberedWithTheCarsOwnerAndPlate() {
        PlateRegistry registry = new PlateRegistry();
        assertTrue(registry.drove(PLAYER, CAR, PLAYER, "standard", "A123BC77"));
        assertFalse(registry.drove(PLAYER, CAR, PLAYER, "standard", "A123BC77"));
        assertEquals(CAR, registry.lastDriven(PLAYER));
        assertEquals("A123BC77", registry.car(CAR).plate());
        assertTrue(registry.taken("A123BC77"));
    }

    @Test
    void aPlateAnotherCarAlreadyHasIsNotTakenOver() {
        PlateRegistry registry = new PlateRegistry();
        registry.seen(CAR, PLAYER, "standard", "A123BC77");
        registry.seen(SECOND, OTHER, "standard", "A123BC77");
        assertEquals("", registry.car(SECOND).plate());
        assertTrue(registry.taken("A123BC77"));
    }

    @Test
    void anAssignedPlateWaitsUntilTheCarTakesIt() {
        PlateRegistry registry = new PlateRegistry();
        registry.seen(CAR, PLAYER, "police", "A123BC77");
        registry.assign(CAR, "X999YX77");
        assertFalse(registry.taken("A123BC77"));
        assertTrue(registry.taken("X999YX77"));
        assertEquals("X999YX77", registry.pending(CAR));
        assertFalse(registry.seen(CAR, PLAYER, "police", "A123BC77"));
        assertEquals("X999YX77", registry.car(CAR).plate());
        assertTrue(registry.applied(CAR, "X999YX77"));
        assertNull(registry.pending(CAR));
    }

    @Test
    void savedRegistryReadsBack() {
        PlateRegistry registry = new PlateRegistry();
        registry.drove(PLAYER, CAR, PLAYER, "medical", "");
        registry.assign(CAR, "M112PP77");
        PlateRegistry read = PlateRegistry.fromJson(registry.toJson());
        assertEquals(registry.car(CAR), read.car(CAR));
        assertEquals(CAR, read.lastDriven(PLAYER));
        assertTrue(read.taken("M112PP77"));
        assertEquals("M112PP77", read.pending(CAR));
    }

    @Test
    void forgottenCarFreesItsPlate() {
        PlateRegistry registry = new PlateRegistry();
        registry.drove(PLAYER, CAR, PLAYER, "standard", "A123BC77");
        assertTrue(registry.forget(CAR));
        assertFalse(registry.taken("A123BC77"));
        assertNull(registry.lastDriven(PLAYER));
    }
}
