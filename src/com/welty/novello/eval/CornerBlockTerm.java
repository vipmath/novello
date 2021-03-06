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

package com.welty.novello.eval;

/**
 * 3x3 corner disks
 *
 * Bit order:
 * corner --> 0 1 2
 *            3 4 5
 *            6 7 8
 */
class CornerBlockTerm extends Term {
    public static CornerBlockTerm[] terms = new CornerBlockTerm[]{new CornerBlockTerm(false, false), new CornerBlockTerm(false, true), new CornerBlockTerm(true, false), new CornerBlockTerm(true, true)};

    private final boolean top;
    private final boolean left;


    public CornerBlockTerm(boolean top, boolean left) {
        super(CornerBlockFeature.instance);
        this.top = top;
        this.left = left;
    }

    @Override public int instance(long mover, long enemy, long moverMoves, long enemyMoves) {
        return instance(mover, enemy, this.left, this.top);
    }

    public static int orid(long mover, long enemy, boolean left, boolean top) {
        return CornerBlockFeature.instance.orid(instance(mover, enemy, left, top));
    }

    private static int instance(long mover, long enemy, boolean left, boolean top) {
        final int moverRow = row(mover, left, top);
        final int enemyRow = row(enemy, left, top);
        return Base3.base2ToBase3(moverRow, enemyRow);
    }

    private static int row(long mover, boolean left, boolean top) {
        if (left) {
            mover = Long.reverse(mover);
        }
        if (left ^ top) {
            mover = Long.reverseBytes(mover);
        }
        final long row = (mover & 0x7) | ((mover & 0x700) >>> 5) | ((mover & 0x70000) >> 10);
        return (int) row;
    }

    @Override String oridGen() {
        return "CornerBlockTerm.orid(mover, enemy, " + left + ", " + top + ")";
    }
}
