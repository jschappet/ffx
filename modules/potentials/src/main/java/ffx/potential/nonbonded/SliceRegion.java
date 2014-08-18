/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2014.
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Force Field X; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ffx.potential.nonbonded;

import java.nio.DoubleBuffer;
import java.util.Arrays;
import java.util.logging.Level;

import edu.rit.pj.IntegerForLoop;
import edu.rit.pj.IntegerSchedule;
import edu.rit.pj.ParallelRegion;

import ffx.crystal.Crystal;
import ffx.potential.bonded.Atom;

import static ffx.potential.nonbonded.SpatialDensityRegion.logger;

/**
 * @author Armin Avdic
 */
public class SliceRegion extends ParallelRegion {

    // A list of atoms.
    Atom atoms[];
    int nAtoms;
    int nSymm;
    int gX, gY, gZ;
    int basisSize;
    int threadCount;
    DoubleBuffer gridBuffer;
    GridInitLoop gridInitLoop[];
    Crystal crystal;
    double initValue = 0.0;
    int gridSize;
    double grid[];

    protected SliceLoop sliceLoop[];
    protected double coordinates[][][];
    protected boolean select[][];

    // Constructor
    public SliceRegion(int gX, int gY, int gZ, double grid[],
            int basisSize, int nSymm,
            int threadCount, Crystal crystal,
            Atom atoms[], double coordinates[][][]) {
        this.atoms = atoms;
        this.nAtoms = atoms.length;
        this.gX = gX;
        this.gY = gY;
        this.gZ = gZ;
        gridSize = gX * gY * gZ * 2;
        this.basisSize = basisSize;
        this.nSymm = nSymm;
        this.threadCount = threadCount;
        this.coordinates = coordinates;
        this.grid = grid;
        if (grid != null) {
            gridBuffer = DoubleBuffer.wrap(grid);
        }
        sliceLoop = new SliceLoop[threadCount];
        gridInitLoop = new GridInitLoop[threadCount];
        for (int i = 0; i < threadCount; i++) {
            gridInitLoop[i] = new GridInitLoop();
        }
        select = new boolean[nSymm][nAtoms];
        for (int i = 0; i < nSymm; i++) {
            Arrays.fill(select[i], true);
        }
    }

    public final void setCrystal(Crystal crystal, int gX, int gY, int gZ) {
        this.crystal = crystal.getUnitCell();
        //assert(this.crystal.spaceGroup.getNumberOfSymOps() == nSymm);
        this.gX = gX;
        this.gY = gY;
        this.gZ = gZ;
        gridSize = gX * gY * gZ * 2;
    }

    public int getNatoms() {
        return nAtoms;
    }

    public void setGridBuffer(DoubleBuffer grid) {
        gridBuffer = grid;
    }

    public int getNsymm() {
        return nSymm;
    }

    public double[] getGrid() {
        return grid;
    }

    @Override
    public void start() {
        selectAtoms();
    }

    @Override
    public void run() throws Exception {
        int threadIndex = getThreadIndex();
        SliceLoop loop = sliceLoop[threadIndex];
        /**
         * This lets the same SpatialDensityLoops be used with different
         * SpatialDensityRegions.
         */
        loop.setNsymm(nSymm);
        try {
            execute(0, gridSize - 1, gridInitLoop[threadIndex]);
            execute(0, gZ - 1, loop);
        } catch (Exception e) {
            String message = " Exception in SliceRegion.";
            logger.log(Level.SEVERE, message, e);
        }
    }

    /**
     * <p>
     * Setter for the field <code>initValue</code>.</p>
     *
     * @param initValue a double.
     */
    public void setInitValue(double initValue) {
        this.initValue = initValue;
    }

    public void setDensityLoop(SliceLoop loops[]) {
        sliceLoop = loops;
    }

    /**
     * Select atoms that should be included. The default is to include all
     * atoms, which is set up in the constructor. This function should be
     * over-ridden by subclasses that want finer control.
     */
    public void selectAtoms() {
        for (int i = 0; i < nSymm; i++) {
            Arrays.fill(select[i], true);
        }
    }

    private class GridInitLoop extends IntegerForLoop {

        private final IntegerSchedule schedule = IntegerSchedule.fixed();
        // Extra padding to avert cache interference.
        long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;
        long pad8, pad9, pada, padb, padc, padd, pade, padf;

        @Override
        public IntegerSchedule schedule() {
            return schedule;
        }

        @Override
        public void run(int lb, int ub) {
            if (gridBuffer != null) {
                //if (grid != null) {
                for (int i = lb; i <= ub; i++) {
                    //grid[i] = initValue;
                    gridBuffer.put(i, initValue);
                }
            }
        }
    }

}
