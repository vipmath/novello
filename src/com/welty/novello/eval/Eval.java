package com.welty.novello.eval;

import com.welty.novello.solver.BitBoard;
import com.welty.novello.solver.BitBoardUtils;

/**
 * The evaluation function evaluates positions.
 * <p/>
 * It also connects the coefficient calculator to the features
 * by mapping feature orid &harr; coefficient calculator index.
 */
public class Eval {
    final CoefficientSet coefficientSet;
    private final EvalStrategy evalStrategy;

    private static final boolean debug = false;

    /**
     */
    public Eval(EvalStrategy evalStrategy, String coeffSetName) {
        this(evalStrategy, new CoefficientSet(evalStrategy, coeffSetName));
    }

    public Eval(EvalStrategy evalStrategy, CoefficientSet coeffSet) {
        this.evalStrategy = evalStrategy;
        coefficientSet = coeffSet;
    }

    /**
     * Evaluate a position.
     * <p/>
     * Passes if necessary; returns terminal value if neither player can move.
     *
     * @param mover mover disks
     * @param enemy enemy disks
     * @return position evaluation
     */
    public int eval(long mover, long enemy) {
        final long moverMoves = BitBoardUtils.calcMoves(mover, enemy);
        final long enemyMoves = BitBoardUtils.calcMoves(enemy, mover);
        final int moveScore;
        if (moverMoves != 0) {
            moveScore = eval(mover, enemy, moverMoves, enemyMoves);
        } else if (enemyMoves != 0) {
            moveScore = -eval(enemy, mover, enemyMoves, moverMoves);
        } else {
            moveScore = CoefficientCalculator.DISK_VALUE * (Long.bitCount(mover) - Long.bitCount(enemy));
        }
        return moveScore;
    }

    /**
     * Evaluate a position.
     * <p/>
     * Precondition: mover has a legal move.
     *
     * @param mover      mover disks
     * @param enemy      enemy disks
     * @param moverMoves legal moves for mover
     * @param enemyMoves legal moves for enemy
     * @return board evaluation, from mover's point of view
     */
    private int eval(long mover, long enemy, long moverMoves, long enemyMoves) {
        if (debug) {
            System.out.println("....................");
            System.out.println(BitBoard.ofMover(mover, enemy, false));
        }
        final int eval = evalStrategy.eval(mover, enemy, moverMoves, enemyMoves, coefficientSet);
        if (debug) {
            System.out.println("Eval = " + eval);
            System.out.println();
        }
        return eval;
    }

    /**
     * Explain its evaluation of a position
     *
     * @param mover      mover disks
     * @param enemy      enemy disks
     * @param moverMoves legal moves for mover
     * @param enemyMoves legal moves for enemy
     */
    public void explain(long mover, long enemy, long moverMoves, long enemyMoves) {
        evalStrategy.explain(mover, enemy, moverMoves, enemyMoves, coefficientSet);
    }

    @Override public String toString() {
        return evalStrategy + "" + coefficientSet;
    }
}