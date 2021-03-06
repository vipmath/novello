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

import com.orbanova.common.math.function.oned.Function;
import com.orbanova.common.misc.Require;
import com.orbanova.common.misc.Vec;

import java.util.ArrayList;
import java.util.List;

/**
 */
public interface Feature {
    /**
     * @return Number of orids (distinct instances) for this feature
     */
    int nOrids();

    /**
     * @return String description of the orid
     */
    String oridDescription(int orid);

    /**
     * @return Number of instances for this feature
     */
    int nInstances();

    /**
     * @return equivalence class of the instance
     */
    int orid(int instance);
}

/**
 * Utility class containing Features that can be used for evaluation
 */
class Features {
    static final SoloFeature cornerFeature = new SoloFeature("CornerMobility",
            "No access to corner",
            "Mover access to corner",
            "Enemy access to corner",
            "Both access to corner",
            "Mover occupies corner",
            "Enemy occupies corner"
    );

    static final SoloFeature corner2Feature = new SoloFeature("corner2",
            "No access to corner",
            "Mover access to corner",
            "Enemy access to corner",
            "Both access to corner",
            "Mover occupies corner",
            "Enemy occupies corner",
            "Mover x-square",
            "Enemy x-square"
    );

    static final Function MOBILITY_WEIGHT = new Function() {

        @Override public double y(double x) {
            return Math.sqrt(x);
        }
    };

    static final SoloFeature moverDisks = new GridFeature("Mover Disks");
    static final SoloFeature enemyDisks = new GridFeature("Enemy Disks");
    static final SoloFeature moverMobilities = new GridFeature("Mover Mobilities");
    static final SoloFeature moverMobilities64 = new GridFeature("Mover Mobilities",64);
    static final SoloFeature enemyMobilities = new GridFeature("Enemy Mobilities");
    static final SoloFeature enemyMobilities64 = new GridFeature("Enemy Mobilities",64);
    static final SoloFeature moverPotMobs = new GridFeature("Mover PotMobs");
    static final SoloFeature enemyPotMobs = new GridFeature("Enemy PotMobs");
    static final SoloFeature moverLinearPotMobs = new GridFeature("Mover PotMobs",64);
    static final SoloFeature enemyLinearPotMobs = new GridFeature("Enemy PotMobs",64);
    static final SoloFeature moverPotMobs2 = new GridFeature("Mover PotMobs2");
    static final SoloFeature enemyPotMobs2 = new GridFeature("Enemy PotMobs2");
    static final SoloFeature parity = new SoloFeature("Parity", "Even", "Odd");


    /**
     * Print a human-readable description of the coefficients to System.out
     *
     * @param feature      feature used to interpret the coefficients
     * @param coefficients coefficients to print
     * @param minValue     minimum absolute value to print a coefficient
     */
    static void dumpCoefficients(Feature feature, short[] coefficients, int minValue) {
        Require.eq(coefficients.length, "# coefficients", feature.nOrids());

        System.out.println();
        System.out.println(feature + ":");
        int nLarge = 0;
        for (int orid = 0; orid < coefficients.length; orid++) {
            final int coefficient = coefficients[orid];
            if (Math.abs(coefficient) >= minValue) {
                final String desc = feature.oridDescription(orid);
                System.out.format("%+5d  %s (orid %d)%n", coefficient, desc, orid);
                nLarge++;
            }
        }
        System.out.println(feature + ": " + nLarge + " coefficients valued at least " + minValue + " out of " + feature.nOrids() + " total coefficients");
    }
}

/**
 * A feature that has a dense weight
 */
interface DenseFeature extends Feature {
    float denseWeight(int orid);
}

/**
 * A feature that combines multiple instances into a single orid via lookup table
 */
class MultiFeature implements Feature {
    private final int[] orids;
    private final String[] oridDescriptions;
    private final String name;

    public MultiFeature(String name, int[] orids, String[] oridDescriptions) {
        this.name = name;
        Require.lt(Vec.max(orids), "maximum orid value", oridDescriptions.length, "number of orid descriptions");
        this.orids = orids;
        this.oridDescriptions = oridDescriptions;
    }

    @Override public int nOrids() {
        return oridDescriptions.length;
    }

    @Override public String oridDescription(int orid) {
        return oridDescriptions[orid];
    }

    @Override public int nInstances() {
        return orids.length;
    }

    @Override public int orid(int instance) {
        return orids[instance];
    }

    @Override public String toString() {
        return name;
    }
}

/**
 * A Feature that has a 1-to-1 mapping between instances and orids.
 */
class SoloFeature implements Feature {
    private final String name;
    private final String[] oridDescriptions;

    public SoloFeature(String name, String... oridDescriptions) {
        this.name = name;
        this.oridDescriptions = oridDescriptions;
    }

    @Override public int orid(int instance) {
        return instance;
    }

    @Override public int nOrids() {
        return oridDescriptions.length;
    }

    @Override public String oridDescription(int orid) {
        return oridDescriptions[orid];
    }

    @Override public int nInstances() {
        return nOrids();
    }

    @Override public String toString() {
        return name;
    }
}

class GridFeature extends SoloFeature {
    public GridFeature(String name) {
        this(name, 65);
    }

    public GridFeature(String name, int nIds) {
        super(name, grid(name, nIds));
    }

    private static String[] grid(String name, int nIds) {
        final String[] result = new String[nIds];
        for (int i = 0; i < nIds; i++) {
            result[i] = String.format("%2d %s", i, name);
        }
        return result;
    }
}
class DenseGridFeature extends GridFeature implements DenseFeature {
    private final Function weightFunction;
    private static final Function IDENTITY = new Function() {
        @Override public double y(double x) {
            //noinspection SuspiciousNameCombination
            return x;
        }
    };

    public DenseGridFeature(String name, Function weightFunction) {
        super(name);
        this.weightFunction = weightFunction;
    }


    @Override
    public float denseWeight(int orid) {
        return (float)weightFunction.y(orid);
    }
}

/**
 * A Feature that uses, as its instance, a base-3 representation of the disks in a line.
 * <p/>
 * 0 = empty, 1=mover, 2=enemy.
 * Any disk pattern gives the same orid as reversing its disks.
 * <p/>
 * Disk patterns are displayed assuming black is the mover; * = mover, O = enemy.
 */
class LinePatternFeatureFactory {
    static Feature of(String name, int nDisks) {
        final int[] orids = new int[Base3.nInstances(nDisks)];
        final List<String> oridDescList = new ArrayList<>();

        int nOrids = 0;
        for (int instance = 0; instance < orids.length; instance++) {
            final int reverse = Base3.reverse(instance, nDisks);
            if (reverse < instance) {
                orids[instance] = orids[reverse];
            } else {
                oridDescList.add(Base3.description(instance, nDisks));
                orids[instance] = nOrids++;
            }
        }

        final String[] oridDescriptions = oridDescList.toArray(new String[oridDescList.size()]);
        return new MultiFeature(name, orids, oridDescriptions);
    }
}