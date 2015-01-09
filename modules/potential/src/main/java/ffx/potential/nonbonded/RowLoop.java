/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2015.
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
 *
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules, and
 * to copy and distribute the resulting executable under terms of your choice,
 * provided that you also meet, for each linked independent module, the terms
 * and conditions of the license of that module. An independent module is a
 * module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but
 * you are not obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */
package ffx.potential.nonbonded;

import java.util.logging.Logger;

import edu.rit.pj.IntegerForLoop;

/**
 * The RowLoop class is used to parallelize placing onto a 3D grid
 *
 * 1) Multipoles using B-splines or
 *
 * 2) Diffraction form factors.
 *
 * Each "row" of the grid (i.e. fixed values of the z and y-coordinates) is
 * operated on by only a single thread to logically enforce atomic updates of
 * grid magnitudes.
 *
 * @author Armin Avdic
 */
public abstract class RowLoop extends IntegerForLoop {

    /**
     * Constant <code>logger</code>
     */
    private static final Logger logger = Logger.getLogger(RowLoop.class.getName());
    private int nAtoms;
    private int nSymm;
    public RowRegion rowRegion;

    public RowLoop(int nAtoms, int nSymm, RowRegion rowRegion) {
        this.nAtoms = nAtoms;
        this.nSymm = nSymm;
        this.rowRegion = rowRegion;
    }

    /**
     * <p>
     * setNsymm</p>
     *
     * @param nSymm a int.
     */
    public void setNsymm(int nSymm) {
        this.nSymm = nSymm;
        assert (nSymm <= rowRegion.nSymm);
    }

    @Override
    public void run(int lb, int ub) throws Exception {
        for (int iSymm = 0; iSymm < nSymm; iSymm++) {
            for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
                if (rowRegion.select[iSymm][iAtom]) {
                    gridDensity(iSymm, iAtom, lb, ub);
                }
            }
        }
    }

    /**
     * Apply electron density "as normal" for an atom, but check that
     * the y and z indeces are within the supplied bounds (inclusive).
     *
     * @param iSymm the SymOp to apply.
     * @param iAtom the index of the Atom to put onto the grid.
     * @param lb the lower bound for the y and z-axes.
     * @param ub the upper bound for the y and z-axes.
     */
    public abstract void gridDensity(int iSymm, int iAtom, int lb, int ub);
}
