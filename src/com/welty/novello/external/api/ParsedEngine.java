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

package com.welty.novello.external.api;

import com.welty.novello.external.engine.ExternalNBoardEngine;
import com.welty.novello.external.gui.ExternalEngineManager;
import com.welty.othello.protocol.ResponseHandler;
import com.welty.othello.protocol.ResponseParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Class that controls communication with an engine.
 */
public class ParsedEngine implements StatelessEngine {
    /**
     * The last ping sent to this engine.
     * <p/>
     * This is used to determine whether the engine is caught up. When there are multiple engines,
     * this variable only cares about its engine.
     */
    private volatile int lastPing;
    private final @NotNull NBoardEngine engine;
    private final @NotNull ResponseParser responseParser;

    public ParsedEngine(@NotNull ExternalEngineManager.Xei xei, boolean debug, @NotNull ResponseHandler responseHandler) throws IOException {
        this(new ResponseParser(responseHandler, xei.name), xei , debug);
    }

    private ParsedEngine(@NotNull ResponseParser responseParser, @NotNull ExternalEngineManager.Xei xei, boolean debug) throws IOException {
        this(responseParser, new ExternalNBoardEngine(xei, debug, responseParser));
    }

    ParsedEngine(@NotNull ResponseParser responseParser, @NotNull NBoardEngine engine) {
        this.responseParser = responseParser;
        this.engine = engine;
        engine.sendCommand("nboard 2");
    }

    @NotNull public String getName() {
        return responseParser.getName();
    }

    /**
     * Request hints (evaluation of the top n moves) from the engine, for the current board
     *
     * @param nMoves number of moves to evaluate
     */
    @Override public synchronized void requestHints(PingPong pingPong, NBoardState state, int nMoves) {
        updateEngineState(pingPong, state);
        engine.sendCommand("hint " + nMoves);
    }

    /**
     * Tell the Engine to learn the current game.
     */
    @Override public synchronized void learn(PingPong pingPong, NBoardState state) {
        updateEngineState(pingPong, state);
        engine.sendCommand("learn");
    }

    /**
     * Request a retrograde analysis from the engine
     */
    @Override public synchronized void analyze(PingPong pingPong, NBoardState state) {
        updateEngineState(pingPong, state);
        engine.sendCommand("analyze");
    }

    /**
     * Request a valid move from the engine, for the current board.
     * <p/>
     * Unlike {@link #requestHints(PingPong, NBoardState, int)}, the engine does not have to return an evaluation;
     * if it has only one legal move it may choose to return that move immediately without searching.
     */
    @Override public synchronized void requestMove(PingPong pingPong, NBoardState state) {
        updateEngineState(pingPong, state);
        engine.sendCommand("go");
    }

    /**
     * Terminate the thread that sends messages to the window.
     * <p/>
     * This is called when the OS copy of the window is about to be destroyed. Sending
     * additional messages to the window could result in crashes.
     */
    @Override public void terminate() {
        engine.sendCommand("quit");
    }

    @Override public @NotNull String getStatus() {
        return responseParser.getStatus();
    }

    @Override public boolean isReady() {
        return responseParser.getPong() >= lastPing;
    }

    private void updateEngineState(PingPong pingPong, NBoardState state) {
        updateState(state);
        synchronized (this) {
            lastPing = pingPong.next();
            engine.sendCommand("ping " + lastPing);
        }
    }

    /**
     * Last contempt value sent to the engine; defaults to 0.
     */
    private int oldContempt = 0;
    private String oldGameText = "";
    private int oldDepth = -1;

    /**
     * Send messages to the engine to update the NBoard state
     *
     * @param state new NBoardState
     */
    void updateState(NBoardState state) {
        final String gameText = state.getGame().toString();
        if (!oldGameText.equals(gameText)) {
            final String moveDiff = calcMoveDiff(oldGameText, gameText);
            if (moveDiff != null) {
                engine.sendCommand("move " + moveDiff);
            } else {
                engine.sendCommand("set game " + gameText);
            }
            oldGameText = gameText;
        }
        if (oldContempt != state.getContempt()) {
            oldContempt = state.getContempt();
            engine.sendCommand("set contempt " + oldContempt);
        }
        if (oldDepth != state.getMaxMidgameDepth()) {
            oldDepth = state.getMaxMidgameDepth();
            engine.sendCommand("set depth " + oldDepth);
        }
    }

    /**
     * @return difference in games, if it's a single move; otherwise null
     */
    private static String calcMoveDiff(String oldGameText, String gameText) {
        if (gameText.length() <= oldGameText.length()) {
            return null;
        }
        if (oldGameText.isEmpty()) {
            return null;
        }
        final int loc = oldGameText.length() - 2;
        if (!gameText.substring(0, loc).equals(oldGameText.substring(0, loc))) {
            return null;
        }
        final String moveDiff = gameText.substring(loc, gameText.length() - 2);
        // need to check how many moves it is
        final int index = moveDiff.indexOf('[');
        final int endIndex = moveDiff.indexOf(']');
        if (index == 1 && endIndex == moveDiff.length() - 1) {
            return moveDiff.substring(index + 1, endIndex);
        } else {
            return null;
        }

    }
}
