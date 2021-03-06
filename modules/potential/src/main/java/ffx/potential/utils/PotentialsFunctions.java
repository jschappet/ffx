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
package ffx.potential.utils;

import java.io.File;

import ffx.potential.ForceFieldEnergy;
import ffx.potential.MolecularAssembly;
import ffx.potential.parsers.SystemFilter;
import java.util.List;

/**
 * The PotentialsFunctions interface specifies utility methods such as opening
 * files into MolecularAssemblys, evaluating energy, and saving assemblies to
 * files. Intended to be analogous to existing Groovy method closures, with both
 * local implementation and a User Interfaces implementation which interacts
 * with our GUI and underlying data structure. This should enable other users to
 * import only Potentials and its dependencies, and slide in their own UI and
 * data structure on top of Potentials.
 *
 * @author Jacob M. Litman
 * @author Michael J. Schnieders
 */
public interface PotentialsFunctions {

    /**
     * True if using a local implementation (not in a user interfaces module).
     *
     * @return If a local implementation
     */
    abstract public boolean isLocal(); // Return true if the local implementation from Potentials.
    
    /**
     * Opens a file and returns all created MolecularAssembly objects.
     *
     * @param file Filename to open
     * @return Array of MolecularAssembly.
     */
    abstract public MolecularAssembly[] open(String file);

    /**
     * Opens an array of files and returns the created MolecularAssembly
     * objects.
     *
     * @param files Filenames to open.
     * @return Array of MolecularAssembly.
     */
    abstract public MolecularAssembly[] open(String[] files);
    
    /**
     * Opens a file and returns all created MolecularAssembly objects, setting
     * any underlying Potential to use a certain number of threads. Default
     * implementation simply ignores nThreads.
     *
     * @param file Filename to open
     * @param nThreads Use non-default num threads
     * @return Array of MolecularAssembly.
     */
    default public MolecularAssembly[] open(String file, int nThreads) {
        return open(file);
    }
    
    /**
     * Opens an array of files and returns all created MolecularAssembly 
     * objects, setting any underlying Potential to use a certain number of 
     * threads. Default implementation simply ignores nThreads.
     *
     * @param file Filenames to open.
     * @param nThreads Use non-default num threads
     * @return Array of MolecularAssembly.
     */
    default public MolecularAssembly[] open(String[] file, int nThreads) {
        return open(file);
    }
    
    /**
     * Converts a data structure (such as a Biojava Structure) into one or more
     * MolecularAssembly objects.
     *
     * @param data Structure to convert
     * @return Array of MolecularAssembly
     */
    abstract public MolecularAssembly[] convertDataStructure(Object data);
    
    /**
     * Converts a data structure (such as a Biojava Structure) into one or more
     * MolecularAssembly objects.
     *
     * @param data Structure to convert
     * @param file Source file
     * @return Array of MolecularAssembly
     */
    abstract public MolecularAssembly[] convertDataStructure(Object data, File file);
    
    /**
     * Converts a data structure (such as a Biojava Structure) into one or more
     * MolecularAssembly objects.
     *
     * @param data Structure to convert
     * @param filename Source file
     * @return Array of MolecularAssembly
     */
    abstract public MolecularAssembly[] convertDataStructure(Object data, String filename);

    /**
     * Performs any necessary shutdown operations on a MolecularAssembly, 
     * primarily shutting down Parallel Java threads and closing any other
     * open resources.
     *
     * @param assembly Assembly to close.
     */
    abstract public void close(MolecularAssembly assembly);

    /**
     * Performs any necessary shutdown operations on an array of 
     * MolecularAssembly, primarily shutting down Parallel Java threads and 
     * closing any other open resources.
     *
     * @param assemblies Assemblies to close.
     */
    abstract public void closeAll(MolecularAssembly[] assemblies);
    
    /**
     * Logs time elapsed since last call.
     *
     * @return Time.
     */
    abstract public double time();

    /**
     * Saves the current state of a MolecularAssembly to an XYZ file.
     *
     * @param assembly MolecularAssembly to save
     * @param file Destination .xyz
     */
    abstract public void save(MolecularAssembly assembly, File file);

    /**
     * Saves the current state of a MolecularAssembly to an XYZ file.
     *
     * @param assembly MolecularAssembly to save
     * @param file Destination .xyz
     */
    abstract public void saveAsXYZ(MolecularAssembly assembly, File file);

    /**
     * Saves the current state of a MolecularAssembly to an XYZ file as a P1
     * crystal.
     *
     * @param assembly MolecularAssembly to save
     * @param file Destination .xyz
     */
    abstract public void saveAsP1(MolecularAssembly assembly, File file);

    /**
     * Saves the current state of a MolecularAssembly to a PDB file.
     *
     * @param assembly MolecularAssembly to save
     * @param file Destination .pdb
     */
    abstract public void saveAsPDB(MolecularAssembly assembly, File file);
    
    /**
     * Saves the current state of an array of MolecularAssemblys to a PDB file.
     *
     * @param assemblies MolecularAssembly array to save
     * @param file Destination .pdb
     */
    abstract public void saveAsPDB(MolecularAssembly[] assemblies, File file);
    
    /**
     * Saves the symmetry mates of a MolecularAssembly to PDB files.
     * 
     * @param assembly To save
     * @param file Destination file
     */
    abstract public void savePDBSymMates(MolecularAssembly assembly, File file);
    // Will use default suffix of _symMate
    
    /**
     * Saves the symmetry mates of a MolecularAssembly to PDB files.
     * 
     * @param assembly To save
     * @param file Destination file
     * @param suffix Custom file suffix
     */
    abstract public void savePDBSymMates(MolecularAssembly assembly, File file, String suffix);

    /**
     * Evaluates the energy of a MolecularAssembly and returns its
     * ForceFieldEnergy object.
     *
     * @param assembly To evaluate
     * @return assembly's ForceFieldEnergy.
     */
    abstract public ForceFieldEnergy energy(MolecularAssembly assembly);

    /**
     * Returns the energy of a MolecularAssembly in kcal/mol (as a double) and
     * prints the energy evaluation
     *
     * @param assembly To evaluate energy of
     * @return Potential energy (kcal/mol)
     */
    abstract public double returnEnergy(MolecularAssembly assembly);
    
    /**
     * Returns the last SystemFilter created by this (may be null).
     * @return Last SystemFilter
     */
    abstract public SystemFilter getFilter();
    
    /**
     * Returns either the active assembly from the overlying UI, or the "active"
     * molecular assembly from the last used SystemFilter.
     * @return A MolecularAssembly or null
     */
    default public MolecularAssembly getActiveAssembly() {
        SystemFilter filt = getFilter();
        if (filt != null) {
            return filt.getActiveMolecularSystem();
        } else {
            return null;
        }
    }
    
    /**
     * If available, returns CLI arguments; default implementation does not have
     * access to CLI arguments, and throws UnsupportedOperationException.
     * @return CLI arguments
     * @throws UnsupportedOperationException If unimplemented
     */
    default public List<String> getArguments() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
    
    //default public String[] 

    // Subsequent methods were when I was duplicating MainPanel's open() methods,
    // instead of its openWait() methods.
    /*abstract public FileOpener open(String file);
     abstract public FileOpener open(String[] files);
     abstract public FileOpener open(File file, String commandDescription);
     abstract public FileOpener open(File[] files, String commandDescription);*/
    
    /**
     * Versions a file, attempting to find an unused filename in the set filename,
     * and filename_1 to filename_999.
     * 
     * @param filename To version
     * @return Versioned filename.
     */
    default public String versionFile(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename must not be null!");
        }
        return versionFile(new File(filename)).getName();
    }
    
    /**
     * Versions a file, attempting to find an unused filename in the set filename,
     * and filename_1 to filename_999.
     * 
     * @param file To version
     * @return Versioned file
     */
    default public File versionFile(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File must not be null!");
        }
        int counter = 1;
        String filename = file.getName();
        while (file.exists() && counter < 1000) {
            file = new File(String.format("%s_%d", filename, counter++));
        }
        if (file.exists()) {
            throw new IllegalArgumentException(String.format("Could not version file %s", filename));
        }
        return file;
    }
}
