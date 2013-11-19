package com.welty.novello.eval;

import com.orbanova.common.misc.ArrayTestCase;
import com.welty.novello.solver.BitBoard;

/**
 */
public class EvalStrategyTest extends ArrayTestCase {
    public void testIndicesFromPosition() throws Exception {
        final EvalStrategy ef = EvalStrategy.eval1;

        // a sample position.
        // square 077 is mover, square 070 is enemy,
        // square 007 has mover access, square 000 has enemy access
        final long mover = 0x8001010101010100L;
        final long enemy = 0x0180808080808000L;
        System.out.println(new BitBoard(mover, enemy, true));
        final int[] expected = {2, 1, 5, 4};
        assertEquals(expected, ef.oridsFromPosition(mover, enemy));
    }
}
