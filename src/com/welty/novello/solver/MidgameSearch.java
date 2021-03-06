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

import com.welty.novello.book.Book;
import com.welty.novello.core.BitBoardUtils;
import com.welty.novello.core.Board;
import com.welty.novello.core.Square;
import com.welty.novello.eval.CoefficientCalculator;
import com.welty.novello.eval.Mpc;
import com.welty.novello.hash.MidgameHashTables;
import com.welty.novello.external.api.AbortCheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import static com.welty.novello.eval.CoefficientCalculator.DISK_VALUE;

public class MidgameSearch {
    public static final int LIMIT = 64 * CoefficientCalculator.DISK_VALUE;

    private final MidgameHashTables midgameHashTables;

    private final @NotNull MidgameSearcher.Options options;
    private final @NotNull Counter counter;

    private final ForkJoinPool pool;

    /**
     * Depth of the search passed to move() or score() by the client.
     */
    private final int rootDepth;

    /**
     * MPC width index passed to move() or score() by the client.
     */
    private final int width;

    /**
     * Opening book, for use in searches.
     */
    @Nullable private final Book book;

    /**
     * Search abort check
     */
    private final AbortCheck abortCheck;

    /**
     * Minimum number of empties at which the search will check in the book for a move
     */
    private final int minBookCheckEmpties;

    MidgameSearch(int nEmpty, MidgameHashTables midgameHashTables, @NotNull MidgameSearcher.Options options,
                  @NotNull Counter counter, ForkJoinPool pool, int rootDepth, int width, @Nullable Book book, AbortCheck abortCheck) {
        this.midgameHashTables = midgameHashTables;
        this.options = options;
        this.counter = counter;
        this.pool = pool;
        this.rootDepth = rootDepth;
        this.width = width;
        this.book = book;
        this.abortCheck = abortCheck;
        minBookCheckEmpties = nEmpty - 3;
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
        BA ba = midgameHashTables.checkForHashCutoff(mover, enemy, depth, alpha, beta, width);
        if (ba != null) {
            return ba;
        }

        final int suggestedMove = getSuggestedMove(mover, enemy, moverMoves, alpha, beta, depth);
        ba = treeMoveWithPossibleSuggestion(mover, enemy, moverMoves, alpha, beta, depth, suggestedMove);
        midgameHashTables.store(mover, enemy, alpha, beta, depth, width, ba.bestMove, ba.score);

        assert ba.isValid(alpha);
        return ba;
    }

    /**
     * @throws SearchAbortedException if the search was aborted
     */
    private int getSuggestedMove(long mover, long enemy, long moverMoves, int alpha, int beta, int depth) throws SearchAbortedException {
        int hashBest = midgameHashTables.getSuggestedMove(mover, enemy);
        if (hashBest >= 0) {
            return hashBest;
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
            ba = treeMoveNoSuggestion(mover, enemy, moverMoves, alpha, beta, depth, new BA(), false);
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

        return treeMoveNoSuggestion(mover, enemy, moverMoves, alpha, beta, depth, ba, true);
    }

    /**
     * @throws SearchAbortedException if the search was aborted
     */
    private BA treeMoveNoSuggestion(long mover, long enemy, long moverMoves, int alpha, int beta, int depth, BA ba, boolean hasSearchedAMove) throws SearchAbortedException {
        if (depth >= 5) {
            final int[] sortIndices = sortMoves(mover, enemy, moverMoves);

            if (depth > 14 && hasSearchedAMove) {
                treeMoveFork(mover, enemy, alpha, beta, depth, ba, sortIndices);
            } else {
                treeMoveSorted(mover, enemy, alpha, beta, depth, ba, sortIndices);
            }

        } else {
            treeMoveUnsorted(mover, enemy, moverMoves, alpha, beta, depth, ba);
        }

        return ba;
    }

    private int[] sortMoves(long mover, long enemy, long moverMoves) throws SearchAbortedException {
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
        return sortIndices;
    }

    private void treeMoveFork(long mover, long enemy, int alpha, int beta, int depth, BA ba, int[] sortIndices) throws SearchAbortedException {
        for (int sortIndex : sortIndices) {
            final int sq = sortIndex & 0xFF;
            final int subScore = calcMoveScore(mover, enemy, alpha, beta, depth, sq);
            if (subScore > ba.score) {
                ba.score = subScore;
                if (subScore > alpha) {
                    ba.bestMove = sq;
                    alpha = subScore;
                    if (subScore >= beta) {
                        return;
                    }
                }
            }
        }
    }

    private class RecursiveSearch extends RecursiveTask<Integer> {

        @Override protected Integer compute() {
            return null;
        }
    }


    private void treeMoveSorted(long mover, long enemy, int alpha, int beta, int depth, BA ba, int[] sortIndices) throws SearchAbortedException {
        for (int sortIndex : sortIndices) {
            final int sq = sortIndex & 0xFF;
            final int subScore = calcMoveScore(mover, enemy, alpha, beta, depth, sq);
            if (subScore > ba.score) {
                ba.score = subScore;
                if (subScore > alpha) {
                    ba.bestMove = sq;
                    alpha = subScore;
                    if (subScore >= beta) {
                        return;
                    }
                }
            }
        }
    }

    private void treeMoveUnsorted(long mover, long enemy, long moverMoves, int alpha, int beta, int depth, BA ba) throws SearchAbortedException {
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
                            return;
                        }
                    }
                }
            }
        }
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
    int searchScore(long mover, long enemy, int alpha, int beta, int depth) throws SearchAbortedException {
        if (depth >= 8 && abortCheck.shouldAbort()) {
            throw new SearchAbortedException();
        }
        if (book!=null && BitBoardUtils.nEmpty(mover, enemy) >= minBookCheckEmpties) {
            final Board board = new Board(mover, enemy, true);
            final Book.Data data = book.getData(board);
            if (data!=null) {
                // right now, always use book data in the search.
                return data.getScore() * CoefficientCalculator.DISK_VALUE;
            }
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

    public static int solverBeta(int beta) {
        assert beta >= -LIMIT;

        if (beta > LIMIT) {
            return 64;
        }
        return (65 * DISK_VALUE + beta - 1) / DISK_VALUE - 64;
    }

    public static int solverAlpha(int alpha) {
        assert alpha <= LIMIT;

        if (alpha < -LIMIT) {
            return -64;
        }
        return (65 * DISK_VALUE + alpha) / DISK_VALUE - 65;
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
        if (options.variableEndgame && nEmpty <= MidgameSearcher.SOLVER_START_DEPTH) {
            final int solverAlpha = solverAlpha(alpha);
            final int solverBeta = solverBeta(beta);
            return ShallowSolver.solveNoParity(counter, mover, enemy, solverAlpha, solverBeta, nEmpty, moverMoves) * DISK_VALUE;
        }

        if (options.mpc && depth >= 2) {
            return mpcMove(mover, enemy, moverMoves, alpha, beta, depth).score;
        }
        return hashMove(mover, enemy, moverMoves, alpha, beta, depth).score;
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

        // see if it cuts off
        BA ba = midgameHashTables.checkForHashCutoff(mover, enemy, depth, alpha, beta, width);
        if (ba != null) {
            return ba;
        }

        ba = new BA();

        final int nEmpty = BitBoardUtils.nEmpty(mover, enemy);
        Mpc.Cutter[] cutters = counter.mpcs.cutters(nEmpty, depth);

        for (Mpc.Cutter cutter : cutters) {
            final int margin = 0;
            final int shallowAlpha = cutter.shallowAlpha(alpha, width) + margin;
            final int shallowBeta = cutter.shallowBeta(beta, width) - margin;
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
        midgameHashTables.store(mover, enemy, alpha, beta, depth, width, ba1.bestMove, ba1.score);

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


}
