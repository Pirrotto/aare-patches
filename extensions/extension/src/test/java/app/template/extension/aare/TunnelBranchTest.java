package app.template.extension.aare;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The branch taken where a tunnel splits: the maneuver's side, straight on, or no choice when unsure. */
public class TunnelBranchTest {
    private static final TunnelBranch.Side L = TunnelBranch.Side.LEFT, R = TunnelBranch.Side.RIGHT,
            S = TunnelBranch.Side.STRAIGHT, U = TunnelBranch.Side.UNKNOWN;

    @Test
    public void sidesOfTheManeuvers() {
        assertEquals(R, TunnelBranch.side("TYPE_KEEP_RIGHT"));
        assertEquals(L, TunnelBranch.side("TYPE_FORK_LEFT"));
        assertEquals(R, TunnelBranch.side("TYPE_OFF_RAMP_SLIGHT_RIGHT"));
        assertEquals(L, TunnelBranch.side("TYPE_TURN_SLIGHT_LEFT"));
        assertEquals(S, TunnelBranch.side("TYPE_STRAIGHT"));
        assertEquals(S, TunnelBranch.side("TYPE_NAME_CHANGE"));
        // Not a choice between branches, or not known.
        assertEquals(U, TunnelBranch.side("TYPE_MERGE_LEFT"));
        assertEquals(U, TunnelBranch.side("TYPE_ROUNDABOUT_ENTER_CW"));
        assertEquals(U, TunnelBranch.side("TYPE_U_TURN_LEFT"));
        assertEquals(U, TunnelBranch.side("TYPE_FERRY_BOAT_RIGHT"));
        assertEquals(U, TunnelBranch.side("TYPE_DESTINATION_LEFT"));
        assertEquals(U, TunnelBranch.side("TYPE_UNKNOWN"));
        assertEquals(U, TunnelBranch.side(null));
    }

    @Test
    public void theBranchOnTheManeuversSide() {
        double[] fork = {-18, 18};
        assertEquals(1, TunnelBranch.choose(fork, R));
        assertEquals(0, TunnelBranch.choose(fork, L));
        // Three ways: the rightmost, clearly.
        assertEquals(2, TunnelBranch.choose(new double[] {-20, 0, 25}, R));
        // Two ways almost as far right: not sure.
        assertEquals(-1, TunnelBranch.choose(new double[] {-20, 20, 25}, R));
    }

    @Test
    public void straightOnOnlyWhenClear() {
        assertEquals(0, TunnelBranch.choose(new double[] {1, 30}, S));
        assertEquals(1, TunnelBranch.choose(new double[] {-25, -2}, S));
        assertEquals(-1, TunnelBranch.choose(new double[] {-18, 18}, S));
        assertEquals(-1, TunnelBranch.choose(new double[] {2, 12}, S));
    }

    @Test
    public void nothingToChooseFrom() {
        assertEquals(-1, TunnelBranch.choose(new double[] {-18, 18}, U));
        assertEquals(-1, TunnelBranch.choose(new double[0], R));
        assertEquals(-1, TunnelBranch.choose(null, R));
        assertEquals(-1, TunnelBranch.choose(new double[] {1, 30}, null));
    }
}
