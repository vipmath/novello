/*
 * Copyright (c) 2014 Chris Welty.
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For the license, see <http://www.gnu.org/licenses/gpl.html>.
 */

package com.welty.novello.solver;

import com.welty.novello.core.*;
import com.welty.novello.eval.CoefficientCalculator;
import com.welty.novello.eval.Mpc;
import com.welty.novello.hash.MidgameHashTables;
import com.welty.othello.api.AbortCheck;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.welty.novello.eval.CoefficientCalculator.DISK_VALUE;

/**
 * A reusable Search object.
 * <p/>
 * This class is not thread-safe.
 */
public class MidgameSearcher {
    public static final int SOLVER_START_DEPTH = 6;
    public static final int LIMIT = 64 * CoefficientCalculator.DISK_VALUE;

    private final MidgameHashTables midgameHashTables = new MidgameHashTables();

    private final @NotNull Options options;
    private final @NotNull Counter counter;

    //////////////////////////////////////////////////////////
    //
    // Mutable options, reset each call to move() or score()
    //
    //////////////////////////////////////////////////////////

    /**
     * Depth of the search passed to move() or score() by the client.
     */
    private int rootDepth;

    /**
     * Search abort check
     */
    private AbortCheck abortCheck;

    /**
     * Create with the given Counter and default options
     *
     * @param counter eval  + counter
     */
    public MidgameSearcher(@NotNull Counter counter) {
        this(counter, new Options(""));
    }

    /**
     * Create with the given counter and options
     *
     * @param counter eval + counter
     * @param options search options
     */
    public MidgameSearcher(@NotNull Counter counter, @NotNull Options options) {
        this.options = options;
        this.counter = counter;
    }

    /**
     * Create with the given counter and options
     *
     * @param counter eval + counter
     * @param options search options
     */
    public MidgameSearcher(Counter counter, String options) {
        this(counter, new Options(options));
    }

    /**
     * @return node counts (flips, evals) since the search was constructed.
     */
    @NotNull public Counts getCounts() {
        return counter.getNodeStats();
    }


    /**
     * Select a move based on a midgame search.
     * <p/>
     * Precondition: The mover has at least one legal move.
     *
     * @param board   position to search
     * @param moverMoves legal moves to check. If this is a subset of all legal moves, only the subset will
     *                   be checked.
     * @param depth      search depth
     * @return the best move from this position, and its score in centi-disks
     * @throws IllegalArgumentException if the position has no legal moves
     */
    public MoveScore getMoveScore(Board board, long moverMoves, int depth)  {
        try {
            return getMoveScore(board, moverMoves, depth, AbortCheck.NEVER);
        } catch (SearchAbortedException e) {
            // this can never happen because we used AbortCheck.NEVER
            throw new IllegalStateException("Shouldn't be here.");
        }
    }

    /**
     * Select a move based on a midgame search.
     * <p/>
     * Precondition: The mover has at least one legal move.
     *
     * @param board   position to search
     * @param moverMoves legal moves to check. If this is a subset of all legal moves, only the subset will
     *                   be checked.
     * @param depth      search depth
     * @param abortCheck test for whether the search should be abandoned
     * @return the best move from this position, and its score in centi-disks
     * @throws IllegalArgumentException if the position has no legal moves
     * @throws SearchAbortedException   if the search was aborted
     */
    public MoveScore getMoveScore(Board board, long moverMoves, int depth, AbortCheck abortCheck) throws SearchAbortedException {
        if (moverMoves == 0) {
            throw new IllegalArgumentException("must have a legal move");
        }
        this.rootDepth = depth;
        this.abortCheck = abortCheck;

        final BA ba = hashMove(board.mover(), board.enemy(), moverMoves, NovelloUtils.NO_MOVE, -NovelloUtils.NO_MOVE, depth);
        String pv = midgameHashTables.extractPv(board, ba.score);
        return new MoveScore(ba.bestMove, ba.score, pv);
    }

    /**
     * Select a move based on a midgame search.
     * <p/>
     * Precondition: The mover has at least one legal move.
     *
     * @param board   position to search
     * @param alpha      search alpha, in centi-disks
     * @param beta       search beta, in centi-disks
     * @param depth      search depth
     * @param abortCheck test for whether the search should be abandoned
     * @return the score of the position in centi-disks
     * @throws SearchAbortedException if the search was aborted
     */
    public int calcScore(Board board, int alpha, int beta, int depth, AbortCheck abortCheck) throws SearchAbortedException {
        return calcScore(board.mover(), board.enemy(), alpha, beta, depth, abortCheck);
    }

    /**
     * @param mover      mover disks
     * @param enemy      enemy disks
     * @param alpha      search alpha, in centi-disks
     * @param beta       search beta, in centi-disks
     * @param depth      remaining search depth, in ply. If &le; 0, returns the eval.
     * @param abortCheck test for whether the search should be abandoned
     * @return score of the position, in centidisks
     * @throws SearchAbortedException if the search was aborted
     */
    private int calcScore(long mover, long enemy, int alpha, int beta, int depth, AbortCheck abortCheck) throws SearchAbortedException {
        this.rootDepth = depth;
        this.abortCheck = abortCheck;

        return searchScore(mover, enemy, alpha, beta, depth);
    }

    /**
     * Return a score estimate based on a midgame search.
     * <p/>
     * The mover does not need to have a legal move - if he doesn't this method will pass or return a terminal value as
     * necessary.
     *
     * @param board position to evaluate
     * @param depth    search depth. If &le; 0, returns the eval.
     * @return score of the move.
     */
    public int calcScore(Board board, int depth) {
        return calcScore(board.mover(), board.enemy(), depth);
    }

    /**
     * Return a score estimate based on a midgame search.
     * <p/>
     * The mover does not need to have a legal move - if he doesn't this method will pass or return a terminal value as
     * necessary.
     * <p/>
     * This is a full-width search that never aborts.
     *
     * @param mover mover disks
     * @param enemy enemy disks
     * @param depth search depth. If &le; 0, returns the eval.
     * @return score of the move.
     */
    public int calcScore(long mover, long enemy, int depth) {
        try {
            return calcScore(mover, enemy, NovelloUtils.NO_MOVE, -NovelloUtils.NO_MOVE, depth, AbortCheck.NEVER);
        } catch (SearchAbortedException e) {
            // this can never happen because we used AbortCheck.NEVER
            throw new IllegalStateException("Shouldn't be here.");
        }
    }

    public void clear() {
        midgameHashTables.clear(63);
    }

    /**
     * calculate a MoveScore for a specific move.
     *
     *
     * @param sq sq of move to check
     * @param subPos position after sq has been played
     * @param alpha (subPos POV)
     * @param beta  (subPos POV)
     * @param subDepth depth remaining from subPos
     * @return MoveScore, POV original position (not subPos).
     * @throws SearchAbortedException
     */
    public MoveScore calcSubMoveScore(int sq, Board subPos, int alpha, int beta, int subDepth, AbortCheck abortCheck) throws SearchAbortedException {
        final int score;
        score = -calcScore(subPos, alpha, beta, subDepth, abortCheck);
        String pv = BitBoardUtils.sqToLowerText(sq) + "-" + midgameHashTables.extractPv(subPos, -score);
        return new MoveScore(sq, score, pv);
    }

    static class BA {
        int bestMove = -1;
        int score = NovelloUtils.NO_MOVE;

        boolean isValid(int alpha) {
            return score <= alpha || bestMove >= 0;
        }
    }

    private static final long[] masks = {BitBoardUtils.CORNERS, ~(BitBoardUtils.CORNERS | BitBoardUtils.X_SQUARES), BitBoardUtils.X_SQUARES};

    /**
     * Find the best move in a position either from the hash table or mpc.
     * <p/>
     * Precondition: The mover is guaranteed to have a move. depth > 0.
     * <p/>
     * The return value is a structure containing a move square and a score
     * The score is a fail-soft alpha beta score. The move square will be -1 if score &lt; alpha
     * and will be a legal move if score >= alpha.
     *
     * @param mover      mover disks
     * @param enemy      enemy disks
     * @param moverMoves mover legal moves
     * @param depth      remaining search depth
     * @return BA see above
     * @throws SearchAbortedException if the search was aborted
     */
    BA hashMove(long mover, long enemy, long moverMoves, int alpha, int beta, int depth) throws SearchAbortedException {
        assert beta > alpha;
        assert depth > 0;

        // see if it cuts off
        final MidgameHashTables.Entry entry = midgameHashTables.find(mover, enemy);
        if (entry != null && entry.getDepth() >= depth) {
            if (entry.getMin() >= beta) {
                final BA ba = new BA();
                ba.bestMove = entry.getBestMove();
                ba.score = entry.getMin();
                return ba;
            }
            if (entry.getMax() <= alpha) {
                final BA ba = new BA();
                ba.bestMove = entry.getBestMove();
                ba.score = entry.getMax();
                return ba;
            }
        }

        final int suggestedMove = getSuggestedMove(mover, enemy, moverMoves, alpha, beta, depth);
        final BA ba = treeMoveWithPossibleSuggestion(mover, enemy, moverMoves, alpha, beta, depth, suggestedMove);
        midgameHashTables.store(mover, enemy, alpha, beta, depth, ba.bestMove, ba.score);

        assert ba.isValid(alpha);
        return ba;
    }

    /**
     * @throws SearchAbortedException if the search was aborted
     */
    private int getSuggestedMove(long mover, long enemy, long moverMoves, int alpha, int beta, int depth) throws SearchAbortedException {
        final MidgameHashTables.Entry entry = midgameHashTables.find(mover, enemy);

        if (entry != null && entry.getBestMove() >= 0) {
            return entry.getBestMove();
        } else if (depth > 2) {
            // internal iterative deepening
            return hashMove(mover, enemy, moverMoves, alpha, beta, depth > 3 ? 2 : 1).bestMove;
        }
        return -1;
    }

    /**
     * @throws SearchAbortedException if the search was aborted
     */
    private BA treeMoveWithPossibleSuggestion(long mover, long enemy, long moverMoves, int alpha, int beta, int depth, int suggestedMove) throws SearchAbortedException {
        final BA ba;
        if (suggestedMove >= 0) {
            ba = treeMoveWithSuggestion(mover, enemy, moverMoves, alpha, beta, depth, suggestedMove);
        } else {
            ba = treeMoveNoSuggestion(mover, enemy, moverMoves, alpha, beta, depth, new BA());
        }
        return ba;
    }

    /**
     * @return best move and score.
     * @throws SearchAbortedException if the search was aborted
     */
    private BA treeMoveWithSuggestion(long mover, long enemy, long moverMoves, int alpha, int beta, int depth, int suggestedMove) throws SearchAbortedException {
        BA ba = new BA();
        final int subScore = calcMoveScore(mover, enemy, alpha, beta, depth, suggestedMove);
        if (subScore > ba.score) {
            ba.score = subScore;
            if (subScore > alpha) {
                ba.bestMove = suggestedMove;
                alpha = subScore;
                if (subScore >= beta) {
                    return ba;
                }
            }
        }
        moverMoves &= ~(1L << suggestedMove);
        return treeMoveNoSuggestion(mover, enemy, moverMoves, alpha, beta, depth, ba);
    }

    /**
     * @throws SearchAbortedException if the search was aborted
     */
    private BA treeMoveNoSuggestion(long mover, long enemy, long moverMoves, int alpha, int beta, int depth, BA ba) throws SearchAbortedException {
        if (depth >= 5) {
            // sortIndices[i] = (-value*256 + sq)
            final int[] sortIndices = new int[Long.bitCount(moverMoves)];
            while (moverMoves != 0) {
                final int sq = Long.numberOfTrailingZeros(moverMoves);
                final long placement = 1L << sq;
                moverMoves ^= placement;
                final int value = calcMoveScore(mover, enemy, -LIMIT, LIMIT, 1, sq);
                final int sortIndex = -value * 256 + sq;
                sortIndices[Long.bitCount(moverMoves)] = sortIndex;
            }
            Arrays.sort(sortIndices);

            for (int sortIndex : sortIndices) {
                final int sq = sortIndex & 0xFF;
                final int subScore = calcMoveScore(mover, enemy, alpha, beta, depth, sq);
                if (subScore > ba.score) {
                    ba.score = subScore;
                    if (subScore > alpha) {
                        ba.bestMove = sq;
                        alpha = subScore;
                        if (subScore >= beta) {
                            return ba;
                        }
                    }
                }
            }
        } else {
            for (long mask : masks) {
                long movesToCheck = moverMoves & mask;
                while (movesToCheck != 0) {
                    final int sq = Long.numberOfTrailingZeros(movesToCheck);
                    final long placement = 1L << sq;
                    movesToCheck ^= placement;
                    final int subScore = calcMoveScore(mover, enemy, alpha, beta, depth, sq);
                    if (subScore > ba.score) {
                        ba.score = subScore;
                        if (subScore > alpha) {
                            ba.bestMove = sq;
                            alpha = subScore;
                            if (subScore >= beta) {
                                return ba;
                            }
                        }
                    }
                }
            }
        }

        return ba;
    }

    /**
     * Make a move on the board and return its value
     *
     * @param depth depth of search, starting at current position
     * @param sq    square of move to make from current position.
     * @return value of the successor position, from current mover's POV.
     * @throws SearchAbortedException if the search was aborted
     */
    private int calcMoveScore(long mover, long enemy, int alpha, int beta, int depth, int sq) throws SearchAbortedException {
        final Square square = Square.of(sq);
        final long flips = counter.calcFlips(square, mover, enemy);

        final long subEnemy = mover | (1L << sq) | flips;
        final long subMover = enemy & ~flips;
        if (options.printSearch) {
            System.out.format("%s[%d] (%+5d,%+5d) scoring(%s):\n", indent(depth), depth, alpha, beta, BitBoardUtils.sqToText(sq));
        }
        final int subScore = -searchScore(subMover, subEnemy, -beta, -alpha, depth - 1);
        if (options.printSearch) {
            System.out.format("%s[%d] (%+5d,%+5d) score(%s)=%+5d\n", indent(depth), depth, alpha, beta, BitBoardUtils.sqToText(sq), subScore);
        }
        return subScore;
    }


    /**
     * Score a position using tree search.
     * <p/>
     * The caller does not need to ensure the mover has a move; if he does not, this routine will handle passing
     * or terminal valuation as necessary.
     *
     * @param depth remaining search depth. If depth &le; 0 this will return the eval.
     * @return score
     * @throws SearchAbortedException if the search was aborted
     */
    private int searchScore(long mover, long enemy, int alpha, int beta, int depth) throws SearchAbortedException {
        if (depth >= 8 && abortCheck.shouldAbort()) {
            throw new SearchAbortedException();
        }
        final long moverMoves = BitBoardUtils.calcMoves(mover, enemy);
        if (moverMoves != 0) {
            final int score = treeScore(mover, enemy, moverMoves, alpha, beta, depth);
            return score;
        } else {
            final long enemyMoves = BitBoardUtils.calcMoves(enemy, mover);
            if (enemyMoves != 0) {
                final int score = treeScore(enemy, mover, enemyMoves, -beta, -alpha, depth);
                return -score;
            } else {
                return DISK_VALUE * BitBoardUtils.terminalScore(mover, enemy);
            }
        }
    }

    /**
     * @throws SearchAbortedException if the search was aborted
     */
    int treeScore(long mover, long enemy, long moverMoves, int alpha, int beta, int depth) throws SearchAbortedException {
        if (depth <= 0) {
            // todo pass moverMoves into counter.eval to save calculation
            return counter.eval(mover, enemy);
        }

        final int nEmpty = BitBoardUtils.nEmpty(mover, enemy);
        if (options.variableEndgame && nEmpty <= SOLVER_START_DEPTH) {
            final int solverAlpha = solverAlpha(alpha);
            final int solverBeta = solverBeta(beta);
            return ShallowSolver.solveNoParity(counter, mover, enemy, solverAlpha, solverBeta, nEmpty, moverMoves) * DISK_VALUE;
        }

        if (options.mpc && depth >= 2) {
            return mpcMove(mover, enemy, moverMoves, alpha, beta, depth).score;
        }
        return hashMove(mover, enemy, moverMoves, alpha, beta, depth).score;
    }

    static int solverBeta(int beta) {
        assert beta >= -LIMIT;

        if (beta > LIMIT) {
            return 64;
        }
        return (65 * DISK_VALUE + beta - 1) / DISK_VALUE - 64;
    }

    static int solverAlpha(int alpha) {
        assert alpha <= LIMIT;

        if (alpha < -LIMIT) {
            return -64;
        }
        return (65 * DISK_VALUE + alpha) / DISK_VALUE - 65;
    }

    /**
     * Search using MPC. Return score and, if available, best move.
     * <p/>
     * Like other routines, this will return -1 if the best move was not available due to alpha cutoff.
     * Unlike other routines, it can also return -1 if score >= beta due to a depth-0 cutoff .
     *
     * @return best move (or -1 if no best move) and score
     */
    private BA mpcMove(long mover, long enemy, long moverMoves, int alpha, int beta, int depth) throws SearchAbortedException {
        final BA ba = new BA();

        // see if it cuts off
        final MidgameHashTables.Entry entry = midgameHashTables.find(mover, enemy);
        if (entry != null && entry.getDepth() >= depth) {
            if (entry.getMin() >= beta) {
                ba.bestMove = entry.getBestMove();
                ba.score = entry.getMin();
                return ba;
            }
            if (entry.getMax() <= alpha || entry.isExact()) {
                ba.bestMove = entry.getBestMove();
                ba.score = entry.getMax();
                return ba;
            }
        }

        final int nEmpty = BitBoardUtils.nEmpty(mover, enemy);
        Mpc.Cutter[] cutters = counter.mpcs.cutters(nEmpty, depth);

        for (Mpc.Cutter cutter : cutters) {
            final int margin = 0;
            final int shallowAlpha = cutter.shallowAlpha(alpha) + margin;
            final int shallowBeta = cutter.shallowBeta(beta) - margin;
            final int shallowDepth = cutter.shallowDepth;
            if (shallowDepth <= 0) {
                final int mpcScore = counter.eval(mover, enemy);
                if (mpcScore >= shallowBeta) {
                    ba.score = beta;
                    return ba;
                }
                if (mpcScore <= shallowAlpha) {
                    ba.score = alpha;
                    return ba;
                }
            } else {
                BA mpcBa = mpcMove(mover, enemy, moverMoves, shallowAlpha, shallowBeta, shallowDepth);
                if (mpcBa.score >= shallowBeta) {
                    ba.score = beta;
                    ba.bestMove = mpcBa.bestMove;
                    return ba;
                }
                if (mpcBa.score <= shallowAlpha) {
                    ba.score = alpha;
                    return ba;
                }
                ba.bestMove = mpcBa.bestMove;
            }
        }

        final int suggestedMove = getSuggestedMove(mover, enemy, moverMoves, alpha, beta, depth);
        final BA ba1 = treeMoveWithPossibleSuggestion(mover, enemy, moverMoves, alpha, beta, depth, suggestedMove);
        midgameHashTables.store(mover, enemy, alpha, beta, depth, ba1.bestMove, ba1.score);

        assert ba1.isValid(alpha);
        return ba1;
    }

    private String indent(int depth) {
        final StringBuilder sb = new StringBuilder();
        for (int i = depth; i < rootDepth; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    /**
     * Midgame search options.
     * <p/>
     * Options are characters interpreted as flags.
     * Current flags are:
     * <p/>
     * S = non-strong engine (don't use variable search depths)<br/>
     * w = full-width search (don't use MPC)<br/>
     * x = experimental<br/>
     */
    public static class Options {
        final boolean mpc;
        public final boolean variableEndgame;
        public final boolean variableMidgame;
        final boolean printSearch;
        public final boolean experimental;

        public Options(String options) {
            mpc = !options.contains("w");
            variableEndgame = !options.contains("S");
            variableMidgame = options.contains("v");
            printSearch = options.contains("p");
            experimental = options.contains("x");
        }
    }
}
