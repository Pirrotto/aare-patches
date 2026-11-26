package app.template.extension.aare;

/**
 * Which way to take where a tunnel of the maps splits, from the navigation app's next maneuver: the
 * branch on the maneuver's side, or the one straight on when there is no maneuver here. Only a clear
 * choice is made; anything in doubt returns -1 and the estimate goes on with the gyroscope.
 * Plain Java, for tests.
 */
final class TunnelBranch {
    private TunnelBranch() {
    }

    enum Side { LEFT, RIGHT, STRAIGHT, UNKNOWN }

    /** The leftmost (or rightmost) branch must turn this much more (degrees) than the next one. */
    static final double CLEAR_DEG = 10;
    /** Straight on: the straightest branch must be this much straighter (degrees) than the others. */
    static final double STRAIGHT_CLEAR_DEG = 15;

    /**
     * The side of a Car App Library maneuver, from its constant's name (Maneuver.TYPE_KEEP_RIGHT,
     * TYPE_FORK_LEFT, TYPE_OFF_RAMP_SLIGHT_RIGHT...). Merges, roundabouts, ferries and the like
     * aren't a choice between branches: UNKNOWN.
     */
    static Side side(String typeName) {
        if (typeName == null) return Side.UNKNOWN;
        if (typeName.equals("TYPE_STRAIGHT") || typeName.equals("TYPE_NAME_CHANGE")) return Side.STRAIGHT;
        if (typeName.contains("MERGE") || typeName.contains("ROUNDABOUT") || typeName.contains("FERRY")
                || typeName.contains("U_TURN")) {
            return Side.UNKNOWN;
        }
        if (typeName.startsWith("TYPE_KEEP_") || typeName.startsWith("TYPE_FORK_") || typeName.startsWith("TYPE_TURN_")
                || typeName.startsWith("TYPE_OFF_RAMP_") || typeName.startsWith("TYPE_ON_RAMP_")) {
            if (typeName.endsWith("_LEFT")) return Side.LEFT;
            if (typeName.endsWith("_RIGHT")) return Side.RIGHT;
        }
        return Side.UNKNOWN;
    }

    /**
     * The branch to take among [turns] (each branch's turn from the current heading, degrees,
     * negative to the left), for a maneuver on [side] here (STRAIGHT when the route goes on without
     * one). -1 when the choice isn't clear.
     */
    static int choose(double[] turns, Side side) {
        if (turns == null || turns.length == 0 || side == null || side == Side.UNKNOWN) return -1;
        if (side == Side.STRAIGHT) {
            int best = -1;
            for (int i = 0; i < turns.length; i++) if (best < 0 || Math.abs(turns[i]) < Math.abs(turns[best])) best = i;
            for (int i = 0; i < turns.length; i++) {
                if (i != best && Math.abs(turns[i]) - Math.abs(turns[best]) < STRAIGHT_CLEAR_DEG) return -1;
            }
            return best;
        }
        // The most to the maneuver's side, clearly more than the next one.
        double sign = side == Side.LEFT ? -1 : 1;
        int best = -1;
        for (int i = 0; i < turns.length; i++) if (best < 0 || sign * turns[i] > sign * turns[best]) best = i;
        for (int i = 0; i < turns.length; i++) {
            if (i != best && sign * turns[best] - sign * turns[i] < CLEAR_DEG) return -1;
        }
        return best;
    }
}
