/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2017.
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
package ffx.algorithms.mc;

import ffx.numerics.Potential;
import ffx.potential.AssemblyState;
import ffx.potential.MolecularAssembly;
import java.util.logging.Logger;

/**
 * The MolecularMC class is a framework to take Monte Carlo steps on a molecular
 * system. It does not implement an MC algorithm, nor does it implement move sets;
 * it is used to evaluate a single MC step with movements defined by implementations
 * of MCMove.
 *
 * @author Michael J. Schnieders
 * @author Jacob M. Litman
 * @since 1.0
 *
 */
public class MolecularMC extends BoltzmannMC {
    private static final Logger logger = Logger.getLogger(MolecularMC.class.getName());
    private final MolecularAssembly mola;
    private final Potential potential;
    private double[] x;
    private AssemblyState initialState;

    /**
     * Constructs a DefaultMC instance with a molecular assembly and its 
     * PotentialEnergy. Fancy footwork will be required if we ever need to use 
     * multiple assemblies at once.
     * @param ma MolecularAssembly to operate on.
     */
    public MolecularMC(MolecularAssembly ma) {
        this(ma, ma.getPotentialEnergy());
    }
    
    /**
     * Constructs a DefaultMC instance with a molecular assembly and a specific
     * Potential.
     * @param ma MolecularAssembly to operate on.
     * @param potential
     */
    public MolecularMC(MolecularAssembly ma, Potential potential) {
        mola = ma;
        this.potential = potential;
    }
    
    /**
     * Returns the associated MolecularAssembly.
     * @return MolecularAssembly
     */
    public MolecularAssembly getMolecularAssembly() {
        return mola;
    }
    
    /**
     * Returns the associated Potential.
     * @return Potential.
     */
    public Potential getPotential() {
        return potential;
    }
    
    @Override
    public void revertStep() {
        initialState.revertState();
    }

    /**
     * Calculates the energy at the current state; identical to RotamerOptimization
     * method of same name.
     *
     * @return Energy of the current state, or 1e100 if an ArithmeticException
     * occurred during calculation.
     */
    @Override
    protected double currentEnergy() {
        if (x == null) {
            int nVar = potential.getNumberOfVariables();
            x = new double[nVar * 3];
        }
        try {
            potential.getCoordinates(x);
            return potential.energy(x);
        } catch (ArithmeticException ex) {
            logger.warning(ex.getMessage());
            return 1e100;
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Default Metropolis Monte Carlo implementation\nTemperature: ");
        sb.append(getTemperature());
        sb.append(String.format("\ne1: %10.6f   e2: %10.6f\nMolecular Assembly", getE1(), getE2()));
        sb.append(mola.toString()).append("\nPotential: ").append(potential.toString());
        return sb.toString();
    }

    @Override
    protected void storeState() {
        initialState = new AssemblyState(mola);
    }
}
