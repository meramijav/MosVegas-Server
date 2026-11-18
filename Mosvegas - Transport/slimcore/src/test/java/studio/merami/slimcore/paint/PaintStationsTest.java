package studio.merami.slimcore.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintStationsTest {
    private static final String WORLD = "world";
    private static final PaintStations.Zone GARAGE = new PaintStations.Zone(0, 65, 0, 6, 68, 9);
    private static final PaintStations.Zone FAR = new PaintStations.Zone(40, 65, 0, 46, 68, 9);

    @Test
    void zoneCoversItsBlocksInclusively() {
        PaintStations.Zone zone = new PaintStations.Zone(4, 65, 5, 10, 68, 12);
        assertTrue(zone.contains(4, 65, 5));
        assertTrue(zone.contains(10.99, 68.99, 12.99));
        assertFalse(zone.contains(11, 65, 5));
        assertFalse(zone.contains(5, 64.9, 6));
    }

    @Test
    void aRegionBecomesOneStationThatMustNotOverlapAnother() {
        PaintStations stations = PaintStations.EMPTY.addRegion(WORLD, "paint", GARAGE);
        assertEquals(1, stations.stations.get(0).id());
        assertEquals("paint", stations.regionStation(WORLD, "paint").region());
        assertEquals("message.slimvehicle.station.region_taken",
                assertThrows(PaintStations.Rejected.class, () -> stations.addRegion(WORLD, "paint", FAR)).key);
        assertEquals("message.slimvehicle.station.zone_small",
                assertThrows(PaintStations.Rejected.class, () -> PaintStations.EMPTY.addRegion(WORLD, "thin", new PaintStations.Zone(0, 65, 0, 1, 68, 9))).key);
        assertEquals("message.slimvehicle.station.zone_overlap",
                assertThrows(PaintStations.Rejected.class, () -> stations.addRegion(WORLD, "next", new PaintStations.Zone(6, 65, 9, 12, 68, 14))).key);
        PaintStations other = stations.addRegion("world_nether", "paint", GARAGE);
        assertEquals(2, other.stations.get(1).id());
    }

    @Test
    void standJoinsTheNearestZoneWithinReachOutsideEveryZone() {
        PaintStations stations = PaintStations.EMPTY.addRegion(WORLD, "near", GARAGE).addRegion(WORLD, "far", FAR);
        PaintStations placed = stations.addStand(WORLD, new PaintStations.Stand(8, 65, 3, 90));
        assertEquals(1, placed.station(1).stands().size());
        assertTrue(placed.station(2).stands().isEmpty());
        assertNotNull(placed.standAt(WORLD, 8, 65, 3));
        assertNull(placed.standAt("world_nether", 8, 65, 3));
        assertEquals("message.slimvehicle.station.stand_in_zone",
                assertThrows(PaintStations.Rejected.class, () -> stations.addStand(WORLD, new PaintStations.Stand(3, 65, 3, 0))).key);
        assertEquals("message.slimvehicle.station.no_zone",
                assertThrows(PaintStations.Rejected.class, () -> stations.addStand(WORLD, new PaintStations.Stand(25, 65, 40, 0))).key);
        assertEquals("message.slimvehicle.station.stand_taken",
                assertThrows(PaintStations.Rejected.class, () -> placed.addStand(WORLD, new PaintStations.Stand(8, 65, 3, 0))).key);
        assertTrue(placed.removeStand(WORLD, 8, 65, 3).station(1).stands().isEmpty());
        assertSame(placed, placed.removeStation(7));
        assertNull(placed.removeStation(1).station(1));
    }

    @Test
    void gunRangeIsMeasuredToTheNearestStandOfTheSameWorld() {
        PaintStations stations = PaintStations.EMPTY.addRegion(WORLD, "paint", GARAGE).addStand(WORLD, new PaintStations.Stand(8, 65, 3, 90));
        assertEquals(25, stations.nearestStandSqr(WORLD, 13.5, 65.5, 3.5), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY, stations.nearestStandSqr("world_nether", 8, 65, 3));
    }

    @Test
    void standFacesThePlayerWhoPlacedIt() {
        assertEquals(180, PaintStations.facingTowards(0));
        assertEquals(0, PaintStations.facingTowards(180));
        assertEquals(270, PaintStations.facingTowards(90));
        assertEquals(90, PaintStations.facingTowards(-90));
        assertEquals(180, PaintStations.facingTowards(30));
    }

    @Test
    void jsonKeepsEveryStationAndStand() {
        PaintStations stations = PaintStations.EMPTY
                .addRegion(WORLD, "paint", GARAGE)
                .addRegion("world_nether", "paint", new PaintStations.Zone(0, 31, 0, 5, 34, 5))
                .addStand(WORLD, new PaintStations.Stand(8, 65, 3, 90))
                .addStand(WORLD, new PaintStations.Stand(-2, 65, 4, 270));
        PaintStations read = PaintStations.fromJson(stations.toJson());
        assertEquals(stations.stations, read.stations);
        assertEquals(1, read.inWorld("world_nether").stations.size());
        assertEquals(stations.toJson(), read.toJson());
    }

    @Test
    void stationsSavedBeforeRegionsLoadWithoutOne() {
        String old = "{\"stations\":[{\"id\":3,\"world\":\"world\",\"zone\":[0,65,0,6,68,9],\"stands\":[[8,65,3,90]]}]}";
        PaintStations.Station station = PaintStations.fromJson(old).station(3);
        assertEquals("", station.region());
        assertEquals(GARAGE, station.zone());
    }
}
