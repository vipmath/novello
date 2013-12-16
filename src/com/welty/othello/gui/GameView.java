package com.welty.othello.gui;

import com.welty.novello.core.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Interface for the game that the Viewer is looking at
 */
public class GameView {
    /**
     * Index of the viewed position in the game (0 = start position, 1 = position after first move or pass)
     */
    private int iPosition;

    private @NotNull MutableGame game;
    /**
     * Engine that is playing black, or null if a human is playing black
     */
    private @Nullable Engine blackEngine;
    /**
     * Engine that is playing white, or null if a human is playing white
     */
    private @Nullable Engine whiteEngine;

    public GameView() {
        game = MutableGame.ofGgf("(;GM[Othello]PC[Rose]PB[NTest:2]PW[b1:3]RE[-64]TI[0]TY[8r]BO[8 -------- -------- -----*-- ---***-- ---**O-- ----**-- -------- -------- O]W[D7/3.85]B[D6/-2.82/0.001]W[C5/4.02]B[C4/-3.87/0.001]W[C7/6.18/0.001]B[G5/-4.55]W[C3/5.03/0.001]B[C6/-2.31]W[E7/8.90/0.001]B[B5/-4.29]W[F7/8.10]B[D3/-5.66/0.001]W[B6/9.18/0.002]B[B3/-6.79]W[G6/12.71/0.001]B[D8/-9.15]W[C8/15.34/0.001]B[E8/-8.62]W[F8/22.57/0.001]B[H6/-12.65]W[A4/23.53/0.002]B[A5/-17.59/0.001]W[A6/33.79/0.001]B[A7/-23.86]W[A8/32.80/0.002]B[B8/-33.98]W[B7/45.62/0.001]B[G7/-39.87]W[B4/46.99/0.001]B[G8]W[H8/56.38]B[PASS]W[C2/64.09]B[B2/-57.44/0.001]W[D2/66.27]B[A2/-62.39]W[A3/65.64/0.001]B[C1/-59.42]W[E3/64.11/0.001]B[E2/-66.55]W[F1/68.21/0.001]B[D1/-65.51]W[A1/71.77/0.001]B[PASS]W[B1/73.14]B[PASS]W[E1/67.18]B[PASS]W[F2/68.59/0.001]B[G2]W[H1/70.47]B[PASS]W[H7/64.00]B[PASS]W[G4/64.00]B[PASS]W[H5/64.00];)");
    }

    /**
     * Get the currently displayed position.
     * <p/>
     * This is not necessarily the game's start position (see {@link #getStartPosition()} or the last position.
     *
     * @return the disks on the board and whether it is white or black's move
     */
    public synchronized @NotNull Position getPosition() {
        return game.getPositionAfter(iPosition);
    }

    /**
     * Move the position pointer to the start of the game
     */
    public synchronized void first() {
        if (iPosition > 0) {
            iPosition = 0;
            fireChange();
        }
    }

    /**
     * Move the position pointer back one move, unless we're at the start of the game.
     */
    public synchronized void prev() {
        if (iPosition > 0) {
            iPosition--;
            fireChange();
        }
    }

    /**
     * Move the position pointer forward one move, unless we're at the end of the game.
     */
    public synchronized void next() {
        if (iPosition < nMoves()) {
            iPosition++;
            fireChange();
        }
    }

    /**
     * Move the position pointer to the end of the game
     */
    public synchronized void last() {
        if (iPosition < nMoves()) {
            iPosition = nMoves();
            fireChange();
        }
    }

    /**
     * @return number of moves in the game, including passes
     */
    public synchronized int nMoves() {
        return game.getMoves().size();
    }

    private final CopyOnWriteArrayList<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Sign up for notifications of Change events.
     *
     * @param listener destination for Change events.
     */
    public void addChangeListener(GameView.ChangeListener listener) {
        // no need to synchronize because listeners is a CopyOnWriteArrayList.
        listeners.add(listener);
    }

    /**
     * Notify all listeners that this has changed
     */
    private void fireChange() {
        // no need to synchronize because listeners is a CopyOnWriteArrayList.
        for (ChangeListener listener : listeners) {
            listener.gameViewChanged();
        }
    }

    /**
     * Set this GameView to the ggf of the given game
     *
     * @param ggf text of the game, in GGF format
     * @throws IllegalArgumentException if the text is not a ggf format game
     */
    public synchronized void setGameGgf(String ggf) {
        game = MutableGame.ofGgf(ggf);
        iPosition = 0;
        fireChange();
    }

    public synchronized int getIPosition() {
        return iPosition;
    }

    public synchronized void setIPosition(int IPosition) {
        this.iPosition = IPosition;
        fireChange();
    }

    public synchronized String getBlackName() {
        return game.blackName;
    }

    public synchronized String getWhiteName() {
        return game.whiteName;
    }

    public synchronized List<Move> getMoves() {
        return new ArrayList<>(game.getMoves());
    }

    public synchronized Position getStartPosition() {
        return game.getStartPosition();
    }

    private long ping;

    /**
     * Start a game.
     * <p/>
     * Set the board position to the start position. Request moves from engines as appropriate.
     *
     * @param blackEngine Engine playing Black
     * @param whiteEngine Engine playing White
     */
    public synchronized void newGame(@Nullable Engine blackEngine, @Nullable Engine whiteEngine) {
        newGame(blackEngine, whiteEngine, Position.START_POSITION);
    }


    /**
     * Start a game.
     * <p/>
     * Set the board position to the start position. Request moves from engines as appropriate.
     *
     * @param blackEngine   Engine playing Black
     * @param whiteEngine   Engine playing White
     * @param startPosition start position for the game
     */
    public void newGame(@Nullable Engine blackEngine, @Nullable Engine whiteEngine, Position startPosition) {
        this.blackEngine = blackEngine;
        this.whiteEngine = whiteEngine;
        game = new MutableGame(startPosition, calcName(blackEngine), calcName(whiteEngine), NovelloUtils.getHostName());
        iPosition = 0;
        requestMove();
        fireChange();
    }

    /**
     * If the player to move at the LastPosition is an Engine, call its requestMove() function
     */
    private void requestMove() {
        if (!isOver()) {
            final Engine engine = currentEngine();
            if (engine != null) {
                engine.requestMove(this, getPosition(), ++ping);
            }
        }
    }

    private @NotNull String calcName(Engine engine) {
        if (engine == null) {
            // a human player!
            return NovelloUtils.getUserName();
        } else {
            return engine.getName();
        }
    }

    /**
     * Update the game.
     * <p/>
     * If the ping does not match the current state of this GameView, the move is considered outdated and thus ignored.
     *
     * Like all GameView methods, this function can be called from any thread.
     *
     * @param moveScore move and Engine score of the move
     * @param ping      ping from Engine.requestMove()
     */
    public synchronized void engineMove(@NotNull MoveScore moveScore, long ping) {
        if (ping == this.ping) {
            game.play(moveScore, 0);
            iPosition = nMoves();
            requestMove();
            fireChange();
        }
    }

    /**
     * The human has clicked on the board.
     * <p/>
     * If not viewing the last position, move the game pointer forward.
     * If it's the human player's move and we're currently viewing the last position, update the game.
     *
     * @param sq The square the human clicked on.
     */
    public synchronized void boardClick(int sq) {
        if (getIPosition() < nMoves()) {
            iPosition++;
            fireChange();
        } else if (isHumansMove()) {
            final long legalMoves = getPosition().calcMoves();
            if (legalMoves == 0) {
                game.pass();
            } else if (BitBoardUtils.isBitSet(legalMoves, sq)) {
                game.play(new MoveScore(sq, 0), 0);
            } else {
                // Human to move, but clicked on a square that is not a legal move. Ignore.
                return;
            }
            iPosition++;
            requestMove();
            fireChange();
        }
    }

    private boolean isHumansMove() {
        return currentEngine() == null && !isOver();
    }

    /**
     * The engine to play at the LastPosition
     * <p/>
     * This looks only at colour; if the game is over at the LastPosition this will still return a value.
     * Call isOver() if you want to know whether the game is over.
     *
     * @return engine or null if the human's move is at the last position.
     */
    private @Nullable Engine currentEngine() {
        return game.getLastPosition().blackToMove ? blackEngine : whiteEngine;
    }

    private boolean isOver() {
        final Position pos = game.getLastPosition();
        return pos.calcMoves() == 0 && pos.enemyMoves() == 0;

    }

    interface ChangeListener {
        /**
         * Do whatever processing is necessary when the game view changes
         */
        void gameViewChanged();
    }
}
