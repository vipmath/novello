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

package com.welty.novello.core;

import static com.welty.novello.core.BitBoardUtils.*;

/**
 * Calculates east/west flips using kindergarten bitboards
 */
public class KindergartenEastWest {
    private static int index(int col, int moverRow, int enemyRow) {
        return (col * 65536 + moverRow * 256 + enemyRow);
    }

    private static final long[] flipTable = createFlipTable();

    private static long[] createFlipTable() {
        long[] flipTable = new long[8 * 256 * 256];
        // flipTable[col*65536 + mover row * 256 + enemy row] = flips
        for (int col = 0; col < 8; col++) {
            for (int moverRow = 0; moverRow < 256; moverRow++) {
                for (int enemyRow = 0; enemyRow < 256; enemyRow++) {
                    if ((moverRow & enemyRow) == 0) {
                        final long empty = ~(moverRow | enemyRow);
                        if (isBitSet(empty, col)) {
                            final long placement = 1L << col;
                            final long flips = BitBoardUtils.fillLR(moverRow, enemyRow, placement);
                            final int index = index(col, moverRow, enemyRow);
                            flipTable[index] = (int) flips;
                        }
                    }
                }
            }
        }
        return flipTable;
    }

    /**
     * @param sq    square index to place disk
     * @param mover mover disks
     * @param enemy enemy disks
     * @return squares that would be flipped in the east/west direction if a mover was placed at sq
     */
    @SuppressWarnings("OctalInteger")
    public static long flips(int sq, long mover, long enemy) {
        final int col = col(sq);
        final int rowTimes8 = sq & 070;
        final int moverRow = 0xFF & (int) (mover >>> rowTimes8);
        final int enemyRow = 0xFF & (int) (enemy >>> rowTimes8);
        final int index = index(col, moverRow, enemyRow);
        final long flips = KindergartenEastWest.flipTable[index] << rowTimes8;
        return flips;
    }
}
