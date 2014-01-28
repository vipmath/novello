package com.welty.ggf;

import junit.framework.TestCase;

/**
 */
public class MoveTest extends TestCase {
    public void testToString() {
        final Move move = new Move("D2/-12/0.103");
        assertEquals("D2/-12.00/0.103", move.toString());

        final Move move2 = new Move("D2", -12., 103 * 0.001);
        assertEquals("D2/-12.00/0.103", move2.toString());

        final Move move3 = new Move("D2", -12, 1);
        assertEquals("D2/-12.00/1.00", move3.toString());

        // these are taken from games downloaded from GGS to try to decode the format exactly
        testToString("a3//19.10");

        testToString("pa");
        testToString("pass");

        testToString("j4//26.22"); // from a 10x10 game
    }

    private void testToString(String s) {
        final Move move = new Move(s);
        assertEquals(s, move.toString());
    }
}