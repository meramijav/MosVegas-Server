package studio.merami.slimcore.paint;

import org.junit.jupiter.api.Test;
import studio.merami.slimcore.baseclasses.DamageModel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintJobTest {
    private static final PaintJob JOB = new PaintJob(UUID.randomUUID(), "Owner", UUID.randomUUID(),
            new PaintShop.Order(0xff0000, Map.of("door_fl", 0x00ff00), PaintShop.NONE), 25000, 1000,
            PaintJob.initialCoats(new PaintShop.Order(0xff0000, Map.of("door_fl", 0x00ff00), PaintShop.NONE)));

    @Test
    void jobGoesStaleTwoMinutesAfterTheLastProgress() {
        assertFalse(JOB.stale(1000 + PaintJob.TIMEOUT_MS - 1));
        assertTrue(JOB.stale(1000 + PaintJob.TIMEOUT_MS));
        assertEquals(120, JOB.secondsLeft(1000));
        assertEquals(1, JOB.secondsLeft(1000 + PaintJob.TIMEOUT_MS - 1));
        assertEquals(0, JOB.secondsLeft(1000 + PaintJob.TIMEOUT_MS + 5000));
    }

    @Test
    void anOrderLeavesOnlyItsAreasToPaint() {
        int body = PaintJob.initialCoats(new PaintShop.Order(0xff0000, Map.of(), PaintShop.NONE));
        assertEquals(0, PaintJob.coat(body, PaintJob.Area.LEFT));
        assertEquals(PaintJob.FULL, PaintJob.coat(body, PaintJob.Area.INTERIOR));
        int interior = PaintJob.initialCoats(new PaintShop.Order(PaintShop.NONE, Map.of(), 0x806040));
        for (PaintJob.Area area : PaintJob.Area.values()) {
            assertEquals(area == PaintJob.Area.INTERIOR ? 0 : PaintJob.FULL, PaintJob.coat(interior, area));
        }
        int both = PaintJob.initialCoats(new PaintShop.Order(0xff0000, Map.of(), 0x806040));
        for (PaintJob.Area area : PaintJob.Area.values()) assertEquals(0, PaintJob.coat(both, area));
    }

    @Test
    void sprayingFillsOneAreaAndResetsTheTimer() {
        PaintJob once = JOB.sprayed(PaintJob.Area.LEFT, 5000);
        assertEquals(PaintJob.STEP, PaintJob.coat(once.coats(), PaintJob.Area.LEFT));
        assertEquals(0, PaintJob.coat(once.coats(), PaintJob.Area.RIGHT));
        assertEquals(5000, once.lastProgress());
        assertEquals(PaintJob.STEP / (float) PaintJob.FULL, PaintJob.share(once.coats(), PaintJob.Area.LEFT));
        assertEquals(0, PaintJob.share(-1, PaintJob.Area.LEFT));
        assertEquals(PaintJob.Area.TOP, PaintJob.Area.of(DamageModel.Side.TOP));
    }

    @Test
    void aFullAreaNoLongerCountsAsProgress() {
        PaintJob side = JOB;
        for (int i = 0; i < PaintJob.FULL / PaintJob.STEP; i++) side = side.sprayed(PaintJob.Area.TOP, 2000 + i);
        assertEquals(PaintJob.FULL, PaintJob.coat(side.coats(), PaintJob.Area.TOP));
        assertSame(side, side.sprayed(PaintJob.Area.TOP, 99_000));
        assertEquals(.2f, PaintJob.progress(side.coats(), true, false), 1e-6);
        assertFalse(side.done());
    }

    @Test
    void everyBodySideFullFinishesABodyJob() {
        PaintJob job = JOB;
        for (PaintJob.Area area : PaintJob.Area.values()) {
            if (area == PaintJob.Area.INTERIOR) continue;
            for (int i = 0; i < PaintJob.FULL / PaintJob.STEP; i++) job = job.sprayed(area, 3000);
        }
        assertTrue(job.done());
        assertEquals(1, PaintJob.progress(job.coats(), true, false));
    }

    @Test
    void anInteriorJobFinishesWhenTheInteriorIsPainted() {
        PaintShop.Order order = new PaintShop.Order(PaintShop.NONE, Map.of(), 0x806040);
        PaintJob job = new PaintJob(UUID.randomUUID(), "Owner", UUID.randomUUID(), order, 9000, 1000, PaintJob.initialCoats(order));
        assertFalse(job.done());
        assertEquals(0, PaintJob.progress(job.coats(), false, true));
        assertSame(job, job.sprayed(PaintJob.Area.LEFT, 2000));
        for (int i = 0; i < PaintJob.FULL / PaintJob.STEP; i++) job = job.sprayed(PaintJob.Area.INTERIOR, 3000);
        assertTrue(job.done());
        assertEquals(1, PaintJob.progress(job.coats(), false, true));
    }

    @Test
    void savedJobsReadBackForRefund() {
        List<PaintJob> read = PaintJob.fromJson(PaintJob.toJson(List.of(JOB)), 7000);
        assertEquals(1, read.size());
        PaintJob back = read.get(0);
        assertEquals(JOB.owner(), back.owner());
        assertEquals(JOB.ownerName(), back.ownerName());
        assertEquals(JOB.vehicle(), back.vehicle());
        assertEquals(JOB.order(), back.order());
        assertEquals(JOB.price(), back.price());
        assertEquals(7000, back.lastProgress());
    }
}
