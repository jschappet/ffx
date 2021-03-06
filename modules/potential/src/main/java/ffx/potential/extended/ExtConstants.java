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
package ffx.potential.extended;

import java.util.Arrays;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * 
 * @author slucore
 */
public class ExtConstants {
    
    public static final List<String> titratableHydrogenNames = 
            Arrays.asList("HH", "HG", "HE2", "HD1", "HE2", "HD2", "HZ3");
    public static final List<String> backboneNames = Arrays.asList("N","CA","C","O","HA","H");
    
    /**
     * Boltzmann's constant is kcal/mol/Kelvin.
     */
    public static final double Boltzmann = 0.0019872041;
    public static final double beta = 1 / Boltzmann;
    /**
     * Boltzmann constant in units of g*Ang**2/ps**2/mole/K.
     */
    public static final double kB = 0.83144725;
    /**
     * Conversion from kcal/mole to g*Ang**2/ps**2.
     */
    public static final double convert = 4.1840e2;
    /**
     * Gas constant (in Kcal/mole/Kelvin).
     */
    public static final double R = 1.9872066e-3;
    /**
     * Random force conversion to kcal/mol/A; formerly randomForce.
     */
    public static final double forceToKcal = sqrt(4.184) / 10e9;
    /**
     * Random force conversion to (kcal/mol/A)^2; formerly randomForce2.
     */
    public static final double forceToKcalSquared = forceToKcal * forceToKcal;
    
    public static final double roomTemperature = 298.15;
    
    public static final double log10 = Math.log(10);
    
}
