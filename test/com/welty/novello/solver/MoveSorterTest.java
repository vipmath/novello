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

import com.welty.novello.selfplay.Players;

/**
 */
public class MoveSorterTest extends BitBoardTestCase {
    /**
     * Test insertion of squares in to the move sorter keeps moves correctly ordered
     */
    public void testInsert() {
        final Counter counter = new Counter(Players.currentEval());
        final MoveSorter sorter = new MoveSorter(counter, new MidgameSearcher(counter));

        sorter.insert(33, 10, 0x88, 0x404, null);
        assertEquals(1, sorter.size());
        final SorterMove sorterMove = sorter.sorterMoves[0];
        assertEquals(33, sorterMove.sq);
        assertEquals(10, sorterMove.score);
        assertEquals(0x88, sorterMove.flips);
        assertEquals(0x404, sorterMove.enemyMoves);

        sorter.insert(34, 11, 0, 0, null);
        checkSquares(sorter, 34, 33);

        sorter.insert(35, 8, 0, 0, null);
        checkSquares(sorter, 34, 33, 35);

        sorter.insert(36, 9, 0, 0, null);
        checkSquares(sorter, 34, 33, 36, 35);

        // duplicate score at end
        sorter.insert(37, 8, 0, 0, null);
        checkSquares(sorter, 34, 33, 36, 35, 37);

        // duplicate score at start
        sorter.insert(38, 11, 0, 0, null);
        checkSquares(sorter, 34, 38, 33, 36, 35, 37);
    }

    /**
     * Check that the sorter has the given squares in the given order
     */
    private void checkSquares(MoveSorter sorter, int... sqs) {
        assertEquals(sqs.length, sorter.size());
        for (int i=0; i<sqs.length; i++) {
            assertEquals("["+i+"]", sqs[i], sorter.sorterMoves[i].sq);
        }
    }
}
