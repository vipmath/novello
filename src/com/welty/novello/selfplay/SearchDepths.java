package com.welty.novello.selfplay;

import com.welty.novello.eval.Mpc;
import com.welty.novello.solver.MidgameSearcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 */
public class SearchDepths {

    private static final List<SearchDepth> DEPTH_1 = Arrays.asList(new SearchDepth(1, Integer.MAX_VALUE, true));

    public static List<SearchDepth> calcSearchDepths(int nEmpty, int maxMidgameDepth) {
        final int probableSolveDepth = nEmpty - (MidgameSearcher.SOLVER_START_DEPTH - 1);

        if (probableSolveDepth <= 1) {
            return DEPTH_1;
        }
        // always search at 1 ply
        final ArrayList<SearchDepth> depths = new ArrayList<>();
        depths.add(new SearchDepth(1, 0, probableSolveDepth));

        // add midgame searches and first probable solve
        final int maxProbDepth = Math.min(probableSolveDepth, maxMidgameDepth);
        for (int depth = 2; depth <= maxProbDepth; depth++) {
            depths.add(new SearchDepth(depth, 0, probableSolveDepth));
        }

        // add probable solves, if we can
//        if (maxMidgameDepth >= probableSolveDepth) {
        // subtract two from lnSolveTime to give a bonus for solves, which are more useful than probable solves.
        final double lnSolveTime = calcLnSolveTime(nEmpty) - 2;

        final double lnMidgameTime = calcLnMidgameTime(maxMidgameDepth);
        // don't add probable solves that will take longer than this cutoff time:
        final double lnCutoff = Math.min(lnSolveTime, lnMidgameTime);

        // If we've already added the first probable solve, don't add it again:
        final int startWidth = maxProbDepth >= probableSolveDepth ? 1 : 0;

        for (int width = startWidth; width <= Mpc.maxWidth(); width++) {
            final double lnProbTime = calcLnTime(nEmpty, width);
            if (lnProbTime > lnCutoff) {
                break;
            }
            depths.add(new SearchDepth(probableSolveDepth, width, true));
        }

        if (lnSolveTime <= lnMidgameTime) {
            depths.add(new SearchDepth(probableSolveDepth, Integer.MAX_VALUE, true));
        }
//        }
        if (depths.size() == 0) {
            throw new IllegalStateException("no depths in SearchDepths.calcDepths()");
        }
        return depths;
    }

    private static double calcLnMidgameTime(int maxMidgameDepth) {
        final int probSolveLimit = maxMidgameDepth + MidgameSearcher.SOLVER_START_DEPTH - 1;
        double lnTime = calcLnTime(probSolveLimit, 0) + 0.1*maxMidgameDepth;
        return lnTime;
    }

    public static SearchDepth lastSearchDepth(int nEmpty, int maxMidgameDepth) {
        final List<SearchDepth> depths = calcSearchDepths(nEmpty, maxMidgameDepth);
        return depths.get(depths.size() - 1);
    }

    /**
     * Estimate the (log of the) amount of time to do a probable solve.
     *
     * @param nEmpty number of empties
     * @param width  search MPC width
     * @return natural log of expected time, in seconds.
     */
    static double calcLnTime(int nEmpty, int width) {
        final double sd = Mpc.widthSd(width);
        final double low = Math.max(0, 2 - sd);
        final double a = 0.52 - 0.2 * low;
        final double d = 33 - 4 * sd + 2 * low;
        return a * (nEmpty - d);
    }

    /**
     * Estimate the (log of the) full-width solve time.
     *
     * @param nEmpty number of empties
     * @return natural log of expected time, in seconds.
     */
    static double calcLnSolveTime(int nEmpty) {
        final double a = 0.84;
        final double d = 21.6;
        return a * (nEmpty - d);
    }

    public static int calcSolveDepth(int midgameDepth) {
        int solveDepth = 0;
        for (int nEmpty = 1; nEmpty <= 60; nEmpty++) {
            if (lastSearchDepth(nEmpty, midgameDepth).isFullSolve()) {
                solveDepth = nEmpty;
            } else {
                break;
            }
        }
        return solveDepth;
    }

    public static String maxes(int midgameDepth) {
        StringBuilder sb = new StringBuilder();
        String prev = null;

        for (int nEmpty = 60; nEmpty >= 1; nEmpty--) {
            final String depth = SearchDepths.lastSearchDepth(nEmpty, midgameDepth).toString();
            if (!depth.equals(prev)) {
                sb.append(String.format("%2d %s\n", nEmpty, depth));
            }
            prev = depth;
        }
        return sb.toString();
    }
}
