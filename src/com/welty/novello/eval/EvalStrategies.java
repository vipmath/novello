package com.welty.novello.eval;

/**
 * Utility class containing EvalStrategy instances
 */
public class EvalStrategies {
    @SuppressWarnings("OctalInteger")
    public static final EvalStrategy eval1 = new EvalStrategy("eval1",
            new CornerTerm(000),
            new CornerTerm(007),
            new CornerTerm(070),
            new CornerTerm(077)
    );

    public static final EvalStrategy diagonalStrategy = new EvalStrategy("diagonal",
            new ULDRTerm(),
            new URDLTerm()
    );
}
