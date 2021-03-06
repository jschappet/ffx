
package ffx.potential

// Groovy Imports
import groovy.cli.Option
import groovy.cli.Unparsed
import groovy.util.CliBuilder

// PJ Imports
import edu.rit.pj.ParallelTeam

// FFX Imports
import ffx.potential.ForceFieldEnergy;
import ffx.potential.utils.PotentialsFunctions
import ffx.potential.utils.PotentialsUtils

/**
 * The Timer script evaluates the wall clock time for energy and forces.
 * <br>
 * Usage:
 * <br>
 * ffxc Timer [options] &lt;filename&gt;
 */
class Timer extends Script {

    /**
     * Options for the Timer script.
     * <br>
     * Usage:
     * <br>
     * ffxc Timer [options] &lt;filename&gt;
     */
    public class Options {
        /**
         * -h or --help to print a help message
         */
        @Option(longName='help', shortName='h', defaultValue='false', description='Print this help message.') boolean help
        /**
         * -n or --iterations to set the number of iterations
         */
        @Option(longName='iterations', shortName='n', defaultValue='5', description='Number of iterations.') int iterations
        /**
         * -c or --threads to set the number of SMP threads (the default of 0 specifies use of all CPU cores)
         */
        @Option(longName='threads', shortName='c', defaultValue='0', description='Number of SMP threads (the default of 0 specifies use of all CPU cores)') int threads
        /**
         * -g or --gradient to ignore computation of the atomic coordinates gradient
         */
        @Option(longName='gradient', shortName='g', defaultValue='false', description='Ignore computation of the atomic coordinates gradient') boolean gradient
        /**
         * -q or --quiet to suppress printing of the energy for each iteration
         */
        @Option(longName='quiet', shortName='q', defaultValue='false', description='Suppress printing of the energy for each iteration') boolean quiet
        /**
         * The final argument(s) should be one or more filenames.
         */
        @Unparsed List<String> filenames
    }

    /**
     * Execute the script.
     */
    def run() {

        // Create the command line parser.
        def cli = new CliBuilder(usage:' ffxc Timer [options] <filename>')
        def options = new Options()
        cli.parseFromInstance(options, args)
        if (options.help == true) {
            return cli.usage()
        }

        List<String> arguments = options.filenames
        String modelFilename = null
        if (arguments != null && arguments.size() > 0) {
            // Read in command line.
            modelFilename = arguments.get(0)
            //open(modelFilename)
        } else if (active == null) {
            return cli.usage()
        } else {
            modelFilename = active.getFile()
        }

        // The number of iterations.
        int nEvals = options.iterations

        // Compute the atomic coordinate gradient.
        boolean noGradient = options.gradient

        // Print the energy for each iteraction.
        boolean quiet = options.quiet

        // Set the number of threads.
        if (options.threads > 0) {
            int nThreads = options.threads
            System.setProperty("pj.nt", nThreads);
        }

        logger.info("\n Timing energy and gradient for " + modelFilename);

        // This is an interface specifying the closure-like methods.
        PotentialsFunctions functions
        try {
            // Use a method closure to try to get an instance of UIUtils (the User Interfaces
            // implementation, which interfaces with the GUI, etc.).
            functions = getPotentialsFunctions()
        } catch (MissingMethodException ex) {
            // If Groovy can't find the appropriate closure, catch the exception and build
            // an instance of the local implementation.
            functions = new PotentialsUtils()
        }
        // Use PotentialsFunctions methods instead of Groovy method closures to do work.
        MolecularAssembly[] assemblies = functions.open(modelFilename)
        MolecularAssembly activeAssembly = assemblies[0]
        ForceFieldEnergy energy = activeAssembly.getPotentialEnergy();

        long minTime = Long.MAX_VALUE;
        double sumTime2 = 0.0;
        int halfnEvals = (nEvals % 2 == 1) ? (nEvals/2) : (nEvals/2) - 1; // Halfway point
        for (int i=0; i<nEvals; i++) {
            long time = -System.nanoTime();
            energy.energy(!noGradient, !quiet);
            time += System.nanoTime();
            minTime = time < minTime ? time : minTime;
            if (i >= (int) (nEvals/2)) {
                double time2 = time * 1.0E-9;
                sumTime2 += (time2*time2);
            }
        }
        ++halfnEvals;
        double rmsTime = Math.sqrt(sumTime2/halfnEvals);
        logger.info(String.format(" Minimum time: %14.5f (sec)", minTime * 1.0E-9));
        logger.info(String.format(" RMS time (latter half): %14.5f (sec)", rmsTime));
    }

}

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
