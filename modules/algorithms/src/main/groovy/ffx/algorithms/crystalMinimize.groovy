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

// MINIMIZE

// Apache Commons Imports
import org.apache.commons.io.FilenameUtils;

// Groovy Imports
import groovy.util.CliBuilder;

// FFX Imports
import ffx.algorithms.CrystalMinimize;
import ffx.potential.ForceFieldEnergy;
import ffx.potential.bonded.Atom;
import ffx.potential.MolecularAssembly;
import ffx.algorithms.CrystalMinimize
import ffx.potential.XtalEnergy

// Default convergence criteria.
double eps = 1.0;

// Things below this line normally do not need to be changed.
// ===============================================================================================

// Create the command line parser.
def cli = new CliBuilder(usage:' ffxc crystalMinimize [options] <filename1>');
cli.h(longOpt:'help', 'Print this help message.');
cli.e(longOpt:'eps', args:1, argName:'1.0', 'RMS gradient convergence criteria');
cli.p(longOpt:'polarization', args:1, 'polarization model: [none / direct / mutual]');
def options = cli.parse(args);

List<String> arguments = options.arguments();
if (options.h || arguments == null || arguments.size() < 1) {
    return cli.usage();
}

// Read in the first command line file.
String filename = arguments.get(0);

// Load convergence criteria.
if (options.e) {
    eps = Double.parseDouble(options.e);
}

if (options.p) {
    System.setProperty("polarization", options.p);
}

// Open the first topology.
systems = open(filename);

logger.info("\n Running minimize on " + filename);
logger.info(" RMS gradient convergence criteria: " + eps);

ForceFieldEnergy forceFieldEnergy = active.getPotentialEnergy();
XtalEnergy xtalEnergy = new XtalEnergy(forceFieldEnergy, active);

// Do the minimization
CrystalMinimize crystalMinimize = new CrystalMinimize(active, xtalEnergy, sh);

e = crystalMinimize.minimize(eps);

String ext = FilenameUtils.getExtension(filename);
filename = FilenameUtils.removeExtension(filename);

if (ext.toUpperCase().contains("XYZ")) {
    saveAsXYZ(new File(filename + ".xyz"));
} else {
    saveAsPDB(systems, new File(filename + ".pdb"));
}
