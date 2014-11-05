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

import com.welty.novello.core.NovelloUtils;

/**
 * A Mutable class containing a best move and a score.
 */
public class BA {
    public int bestMove = -1;
    public int score = NovelloUtils.NO_MOVE;

    public boolean isValid(int alpha) {
        return score <= alpha || bestMove >= 0;
    }
}
