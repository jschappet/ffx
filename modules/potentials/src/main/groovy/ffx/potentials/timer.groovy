/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2012.
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

// TIMER

// Groovy Imports
import groovy.util.CliBuilder;

// FFX Imports
import ffx.potential.ForceFieldEnergy;

// The number of iterations.
int nEvals = 5;

// Compute the atomic coordinate gradient.
boolean gradient = true;

// Print the energy for each iteraction.
boolean print = true;

// Things below this line normally do not need to be changed.
// ===============================================================================================

// Create the command line parser.
def cli = new CliBuilder(usage:' ffxc timer [options] <filename>');
cli.h(longOpt:'help', 'Print this help message.');
cli.n(longOpt:'iterations', args:1, argName:'5', 'Number of iterations');
cli.c(longOpt:'threads', args:1, argName:'all', 'Number of SMP threads (ie. default uses all CPU cores)');
cli.g(longOpt:'gradient', args:1, argName:'true', 'Compute the atomic coordinats gradeint');
cli.v(longOpt:'verbose', args:1, argName:'true', 'Print out the energy for each step');

def options = cli.parse(args);

List<String> arguments = options.arguments();
if (options.h || arguments == null || arguments.size() != 1) {
    return cli.usage();
}

// Load the number iterations.
if (options.n) {
    nEvals = Integer.parseInt(options.n);
}

// Load the number of threads.
if (options.c) {
    System.setProperty("pj.nt", options.c);
}

// Compute the gradient for each step.
if (options.g) {
    gradient = Boolean.parseBoolean(options.g);
}

// Print the energy for each step.
if (options.v) {
    print = Boolean.parseBoolean(options.v);
}

// Read in command line.
String filename = arguments.get(0);

logger.info("\n Timing energy and gradient for " + filename);

open(filename);

ForceFieldEnergy energy = active.getPotentialEnergy();

for (int i=0; i<nEvals; i++) {
    energy.energy(gradient, print);
}