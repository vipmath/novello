package com.welty.novello.core;

import com.welty.novello.solver.BitBoardTestCase;

/**
 */
public class PositionTest extends BitBoardTestCase {
    public void testConstructor() {
        final Position bb = new Position(0x01020304050607L, 0x10203040506070L, true);
        final String positionString = bb.positionString();
        assertEquals(bb, new Position(positionString));
    }
    public void testMinimalReflection() {
        final long black = 0x3141592653589793L;
        final long white = 0x2718281828459045L &~black;

        final Position bb = new Position(black, white, true);
        final Position minimal = bb.minimalReflection();
        for (int r=0; r<8; r++) {
            final Position reflection = bb.reflection(r);
            assertTrue(minimal.compareTo(reflection)<0 || minimal.equals(reflection));
        }
    }
}