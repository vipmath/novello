package com.welty.novello.eval;

import com.welty.novello.core.BitBoardUtils;

import java.util.*;

/**
 * Utility class containing EvalStrategy instances
 */
@SuppressWarnings("OctalInteger")
public class EvalStrategies {
    private static StrategyStore store = new StrategyStore();

    public static EvalStrategy strategy(String name) {
        return store.getStrategy(name);
    }

    public static void addStrategy(String name, EvalStrategy evalStrategy) {
        store.putStrategy(name, evalStrategy);
    }


    /**
     * Returns all known strategies at the time the call is made.
     * <p/>
     * If strategies are added after this function returns, the Iterable will not be updated to reflect that change.
     *
     * @return all strategies that have been registered.
     */
    public static Iterable<EvalStrategy> knownStrategies() {
        return store.values();
    }

    @SuppressWarnings("OctalInteger")
    public static final EvalStrategy eval1 = new EvalStrategy("eval1",
            new CornerTerm(000),
            new CornerTerm(007),
            new CornerTerm(070),
            new CornerTerm(077)
    );

    public static final EvalStrategy diagonal = new EvalStrategy("diagonal",
            new UldrTerm(0),
            new UrdlTerm(0)
    );


    static {
        new EvalStrategy("a",
                new CornerTerm2(000),
                new CornerTerm2(007),
                new CornerTerm2(070),
                new CornerTerm2(077),
                Terms.moverDisks,
                Terms.enemyDisks,
                Terms.moverMobilities,
                Terms.enemyMobilities,
                Terms.moverPotMobs,
                Terms.enemyPotMobs,
                Terms.moverPotMobs2,
                Terms.enemyPotMobs2,
                new RowTerm(0),
                new RowTerm(1),
                new RowTerm(2),
                new RowTerm(3),
                new RowTerm(4),
                new RowTerm(5),
                new RowTerm(6),
                new RowTerm(7),
                new ColTerm(0),
                new ColTerm(1),
                new ColTerm(2),
                new ColTerm(3),
                new ColTerm(4),
                new ColTerm(5),
                new ColTerm(6),
                new ColTerm(7),
                new UldrTerm(-4),
                new UldrTerm(-3),
                new UldrTerm(-2),
                new UldrTerm(-1),
                new UldrTerm(-0),
                new UldrTerm(1),
                new UldrTerm(2),
                new UldrTerm(3),
                new UldrTerm(4),
                new UrdlTerm(-4),
                new UrdlTerm(-3),
                new UrdlTerm(-2),
                new UrdlTerm(-1),
                new UrdlTerm(-0),
                new UrdlTerm(1),
                new UrdlTerm(2),
                new UrdlTerm(3),
                new UrdlTerm(4)
        );
        new EvalStrategyB();
    }

    public static class StrategyStore {
        private final HashMap<String, EvalStrategy> strategyFromName = new HashMap<>();

        private synchronized void putStrategy(String name, EvalStrategy evalStrategy) {
            strategyFromName.put(name, evalStrategy);
        }

        private synchronized EvalStrategy getStrategy(String name) {
            final EvalStrategy strategy = strategyFromName.get(name);
            if (strategy == null) {
                throw new IllegalArgumentException("unknown strategy name : " + name);
            }
            return strategy;
        }

        private synchronized Collection<EvalStrategy> values() {
            return new ArrayList<>(strategyFromName.values());
        }
    }

    private static class EvalStrategyB extends EvalStrategy {
        private final CornerTerm2[] cornerTerms;
        private final RowTerm[] rowTerms;

        public EvalStrategyB() {
            this(cornerTerms2(),
                    new RowTerm[]{new RowTerm(0), new RowTerm(1), new RowTerm(2), new RowTerm(3), new RowTerm(4), new RowTerm(5), new RowTerm(6), new RowTerm(7)});
        }

        public EvalStrategyB(CornerTerm2[] cornerTerms, RowTerm[] rowTerms) {
            super("b",
                    flatten(cornerTerms,
                            Terms.moverDisks, Terms.enemyDisks, Terms.moverMobilities, Terms.enemyMobilities,
                            Terms.moverPotMobs, Terms.enemyPotMobs, Terms.moverPotMobs2, Terms.enemyPotMobs2,
                            rowTerms,
                            new ColTerm(0), new ColTerm(1), new ColTerm(2), new ColTerm(3), new ColTerm(4), new ColTerm(5), new ColTerm(6), new ColTerm(7),
                            new UldrTerm(-4), new UldrTerm(-3), new UldrTerm(-2), new UldrTerm(-1), new UldrTerm(-0), new UldrTerm(1), new UldrTerm(2), new UldrTerm(3), new UldrTerm(4),
                            new UrdlTerm(-4), new UrdlTerm(-3), new UrdlTerm(-2), new UrdlTerm(-1), new UrdlTerm(-0), new UrdlTerm(1), new UrdlTerm(2), new UrdlTerm(3), new UrdlTerm(4),
                            new CornerBlockTerm(false, false), new CornerBlockTerm(false, true), new CornerBlockTerm(true, false), new CornerBlockTerm(true, true)
                    )
            );
            this.cornerTerms = cornerTerms;
            this.rowTerms = rowTerms;
        }

        @Override
        int eval(long mover, long enemy, long moverMoves, long enemyMoves, CoefficientSet coefficientSet) {
            assert moverMoves != 0;

            final int[][] slice = coefficientSet.slice(BitBoardUtils.nEmpty(mover, enemy));

            int eval = 0;

            // evaluate corner features separately to see if specialization helps the timing
            final int iCornerFeature = 0;
            final Feature cornerFeature = cornerTerms[0].getFeature();
            final int[] cornerFeatureCoeffs = slice[iCornerFeature];
            for (final CornerTerm2 term : cornerTerms) {
                final int instance = term.instance(mover, enemy, moverMoves, enemyMoves);
                final int orid = cornerFeature.orid(instance);
                final int coeff = cornerFeatureCoeffs[orid];
                eval += coeff;
            }

            eval += slice[1][Terms.moverDisks.instance(mover, enemy, moverMoves, enemyMoves)];
            eval += slice[2][Terms.enemyDisks.instance(mover, enemy, moverMoves, enemyMoves)];
            eval += slice[3][Terms.moverMobilities.instance(mover, enemy, moverMoves, enemyMoves)];
            eval += slice[4][Terms.enemyMobilities.instance(mover, enemy, moverMoves, enemyMoves)];
            eval += slice[5][Terms.moverPotMobs.instance(mover, enemy, moverMoves, enemyMoves)];
            eval += slice[6][Terms.enemyPotMobs.instance(mover, enemy, moverMoves, enemyMoves)];
            eval += slice[7][Terms.moverPotMobs2.instance(mover, enemy, moverMoves, enemyMoves)];
            eval += slice[8][Terms.enemyPotMobs2.instance(mover, enemy, moverMoves, enemyMoves)];

            final int iRow0Feature = 9;
            final int iRow1Feature = 10;
            final int iRow2Feature = 11;
            final int iRow3Feature = 12;
            
            final Feature row0Feature = rowTerms[0].getFeature();
            final Feature row1Feature = rowTerms[1].getFeature();
            final Feature row2Feature = rowTerms[2].getFeature();
            final Feature row3Feature = rowTerms[3].getFeature();
            
            final int[] row0FeatureCoeffs = slice[iRow0Feature];
            final int[] row1FeatureCoeffs = slice[iRow1Feature];
            final int[] row2FeatureCoeffs = slice[iRow2Feature];
            final int[] row3FeatureCoeffs = slice[iRow3Feature];

            eval += row0FeatureCoeffs[row0Feature.orid(rowTerms[0].instance(mover, enemy, moverMoves, enemyMoves))];
            eval += row1FeatureCoeffs[row1Feature.orid(rowTerms[1].instance(mover, enemy, moverMoves, enemyMoves))];
            eval += row2FeatureCoeffs[row2Feature.orid(rowTerms[2].instance(mover, enemy, moverMoves, enemyMoves))];
            eval += row3FeatureCoeffs[row3Feature.orid(rowTerms[3].instance(mover, enemy, moverMoves, enemyMoves))];
            eval += row0FeatureCoeffs[row0Feature.orid(rowTerms[7].instance(mover, enemy, moverMoves, enemyMoves))];
            eval += row1FeatureCoeffs[row1Feature.orid(rowTerms[6].instance(mover, enemy, moverMoves, enemyMoves))];
            eval += row2FeatureCoeffs[row2Feature.orid(rowTerms[5].instance(mover, enemy, moverMoves, enemyMoves))];
            eval += row3FeatureCoeffs[row3Feature.orid(rowTerms[4].instance(mover, enemy, moverMoves, enemyMoves))];


            for (int iTerm = 20; iTerm < terms.length; iTerm++) {
                final Term term = terms[iTerm];
                final int iFeature = iFeatures[iTerm];

                final int orid = term.orid(mover, enemy, moverMoves, enemyMoves);

                final int coeff = slice[iFeature][orid];
                eval += coeff;
            }
            return eval;
        }

        private static Term[] flatten(Object... others) {
            final ArrayList<Term> terms = new ArrayList<>();
            for (Object o : others) {
                if (o instanceof Term[]) {
                    terms.addAll(Arrays.asList((Term[])o));
                }
                else if (o instanceof Term) {
                    terms.add((Term)o);
                }
                else {
                    throw new IllegalStateException("oops. " + o.getClass());
                }
            }
            return terms.toArray(new Term[terms.size()]);
        }
    }

    private static CornerTerm2[] cornerTerms2() {
        return new CornerTerm2[]{new CornerTerm2(000), new CornerTerm2(007), new CornerTerm2(070), new CornerTerm2(077)};
    }
}
