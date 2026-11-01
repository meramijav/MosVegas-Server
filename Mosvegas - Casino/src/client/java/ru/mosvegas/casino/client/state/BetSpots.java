package ru.mosvegas.casino.client.state;

import ru.mosvegas.casino.game.blackjack.SideBet;
import ru.mosvegas.casino.net.TableSnapshot;
import ru.mosvegas.casino.table.TableGeometry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a participant's chips lie on the felt (table-local): key -1 is the main bet, other keys are
 * {@link SideBet} ordinals. Shared by the chip renderer and the click-on-the-table betting of the menu.
 */
public final class BetSpots {
    public static final int MAIN = -1;

    private BetSpots() {}

    public static Map<Integer, TableGeometry.Local> of(TableGeometry g, TableSnapshot s, TableSnapshot.Player p) {
        Map<Integer, TableGeometry.Local> spots = new LinkedHashMap<>();
        int box = p.box();
        TableGeometry.Local spot = g.betSpot(box), t = g.tangent(box), in = g.inward(box);
        double x = spot.x(), z = spot.z();
        if (!p.owner()) {
            // bets behind are stacked around the main spot, in the order the people joined
            int behind = 0;
            for (TableSnapshot.Player q : s.atBox(box)) {
                if (q.owner()) continue;
                behind++;
                if (q.id().equals(p.id())) break;
            }
            double side = behind % 2 == 1 ? -1 : 1;
            double ring = (behind + 1) / 2;
            x += -in.x() * 0.11 * ring + t.x() * side * 0.07;
            z += -in.z() * 0.11 * ring + t.z() * side * 0.07;
        }
        spots.put(MAIN, new TableGeometry.Local(x, z));
        if (p.owner()) {
            int index = 0;
            for (SideBet bet : SideBet.values()) {
                if ((s.sideBetMask() & (1 << bet.ordinal())) == 0) continue;
                TableGeometry.Local ss = g.sideSpot(box, index);
                double r = index >= 2 ? 0.12 : 0;
                spots.put(bet.ordinal(), new TableGeometry.Local(ss.x() - in.x() * r, ss.z() - in.z() * r));
                index++;
            }
        }
        return spots;
    }

    /** Click radius of a spot (the printed main square is 0.2 wide, side circles 0.1). */
    public static double radius(int spot) { return spot == MAIN ? 0.13 : 0.07; }
}
