package com.welty.novello.selfplay;

import com.welty.novello.solver.BitBoard;
import com.welty.novello.solver.Result;
import com.welty.novello.solver.Solver;
import org.jetbrains.annotations.NotNull;

/**
 * Play randomly except play perfectly with <= 8 empties
 */
public class EndgamePlayer extends RandomPlayer {
    private static final ThreadLocal<Solver> solvers = new ThreadLocal<Solver>() {
        @Override protected Solver initialValue() {
            return new Solver();
        }
    };

    @Override public int calcMove(@NotNull BitBoard board, long moverMoves, int flags) {
        if (board.nEmpty() <= 8) {
            return solveMove(board);
        }
        else {
            return super.calcMove(board, moverMoves, flags);
        }
    }

    /**
     * @param board board to move from
     * @return the perfect-play move
     */
    protected int solveMove(BitBoard board) {
        final Result result = solvers.get().solveWithMove(board.mover(), board.enemy());
        return result.bestMoveSq;
    }
}
