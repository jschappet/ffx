
/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2016.
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


// Groovy Imports
import groovy.util.CliBuilder
import groovy.lang.MissingPropertyException

//FFX Imports
import ffx.autoparm.Fragmenter
import ffx.autoparm.Wizard

/**
 * SDF to SMILES Converter
 * Auto-fragmenting algorithm
 * 
 * @author Rae Ann Corrigan
 */

// Things below this line normally do not need to be changed.
// ===============================================================================================

// Create the command line parser.
def cli = new CliBuilder(usage:' ffxc fragment [options] <filename>');
cli.h(longOpt:'help', 'Print this help message.');
def options = cli.parse(args);

List<String> arguments = options.arguments();
//if (options.h || arguments == null || arguments.size() != 1){ original code
if (options.h || arguments == null) {
    return cli.usage();
} 

// Read in command line.
String sdffile = arguments.get(0);
String smi = new String();

Wizard wi = new Wizard(sdffile);
smi = wi.readSDF();

System.out.println("\nFinished Wizard, calling Fragmenter\n");
Fragmenter fr = new Fragmenter(sdffile, smi);
fr.readSDF();

return;
