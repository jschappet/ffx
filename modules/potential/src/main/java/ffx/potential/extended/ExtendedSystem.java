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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;

import edu.rit.pj.reduction.SharedDouble;

import ffx.numerics.Potential;
import ffx.potential.ForceFieldEnergy;
import ffx.potential.MolecularAssembly;
import ffx.potential.bonded.Atom;
import ffx.potential.bonded.BondedTerm;
import ffx.potential.extended.ExtUtils.SB;
import ffx.potential.extended.ExtendedVariable.AtomList;
import ffx.potential.nonbonded.ParticleMeshEwald;
import ffx.potential.nonbonded.ParticleMeshEwaldQI;
import ffx.potential.nonbonded.VanDerWaals;
import ffx.potential.parameters.ForceField;

import static ffx.potential.extended.ExtUtils.logf;
import static ffx.potential.extended.ExtUtils.prop;

/**
 *
 * @author slucore
 */
public class ExtendedSystem implements Iterable<ExtendedVariable> {

    private static final Logger logger = Logger.getLogger(ExtendedSystem.class.getName());
    
    // Properties: Static Final
    public static final boolean esvTempOverride = prop("esv-tempOverride", true);
    public static final boolean esvUsePme = prop("esv-usePme", false);
    public static final boolean esvUseVdw = prop("esv-useVdw", true);
    public static final boolean ffePrintOverride = prop("ffe-printOverride", false);
    public static final boolean esvScaleBonded = prop("esv-scaleBonded", true);
    public static final boolean esvDecomposeBonded = prop("esv-decomposeBonded", true);
    // Properties: Mutable
    private boolean esvBiasTerm = prop("esv-biasTerm", true);

    // Atom Lists
    /**
     * Foreground/background atom lists and arrays based on the 
     * "Titratable Hydrogens Only" inclusion criterion.
     * Heavy atoms of deprotonated forms are not present. Used by VdW.
     */
    private List<Atom> foregroundExtH, backgroundExtH;
    private Atom[] atomsExtH;
    private int[] moleculeExtH;
    private int nAtomsExtH;
    private ExtendedVariable[] esvByAtomExtH;
    /**
     * Foreground/background atoms lists and arrays based on the 
     * "All Atoms" inclusion criterion. Used by bonded terms.
     */
    private List<Atom> foregroundExtAll, backgroundExtAll;
    private Atom[] atomsExtAll;
    private int[] moleculeExtAll;
    private int nAtomsExtAll;
    private ExtendedVariable[] esvByAtomExtAll;
    
    // ESV variables
    private int numESVs;
    private List<ExtendedVariable> esvList;
    private boolean esvTerm, phTerm, vdwTerm, mpoleTerm;
    private Double propagationTemperature;
    
    // Potential Objects
    private final MolecularAssembly mola;
    private final ForceFieldEnergy ffe;
    private final VanDerWaals vdw;
    private final ParticleMeshEwaldQI pme;  // must not be global frame

    public ExtendedSystem(MolecularAssembly molecularAssembly) {
        if (molecularAssembly == null) {
            throw new IllegalArgumentException();
        }
        mola = molecularAssembly;
        Potential potential = mola.getPotentialEnergy();
        if (!(potential instanceof ForceFieldEnergy)) {
            logger.severe("ExtendedSystem supported only for ForceFieldEnergy potentials.");
        }
        ffe = (ForceFieldEnergy) potential;
        ffe.setPrintOverride(ffePrintOverride);
        
        ForceField ff = mola.getForceField();
        vdwTerm = ff.getBoolean(ForceField.ForceFieldBoolean.VDWTERM, true);
        mpoleTerm = ff.getBoolean(ForceField.ForceFieldBoolean.MPOLETERM, false);   // TODO set default true

        vdw = (vdwTerm && esvUseVdw) ? ffe.getVdwNode() : null;
//        extendedNeighborList = (vdw != null) ? vdw.getExtendedNeighborList() : null;
        
        if (mpoleTerm && esvUsePme) {
            ParticleMeshEwald pmeNode = ffe.getPmeNode();
            if (pmeNode instanceof ParticleMeshEwaldQI) {
                pme = (ParticleMeshEwaldQI) pmeNode;
            } else {
                logger.severe("Extended system supported only for quasi-internal ParticleMeshEwald.");
                pme = null;
            }
        } else {
            pme = null;
        }
        
        esvList = new ArrayList<>();
        propagationTemperature = ExtConstants.roomTemperature;
        
        // Hydrogens-only inclusion criterion.
        foregroundExtH = new ArrayList<>();
        backgroundExtH = new ArrayList<>();
        foregroundExtH.addAll(Arrays.asList(mola.getAtomArray()));
        atomsExtH = mola.getAtomArray();
        moleculeExtH = mola.getMoleculeNumbers();
        // All-atom inclusion criterion.
        foregroundExtAll = new ArrayList<>();
        backgroundExtAll = new ArrayList<>();
        foregroundExtAll.addAll(Arrays.asList(mola.getAtomArray()));
        atomsExtAll = mola.getAtomArray();
        moleculeExtAll = mola.getMoleculeNumbers();        
    }
    
    public boolean isExtH(int i) {
        return esvByAtomExtH[i] != null 
                && esvByAtomExtH[i].getUnsharedAtoms().contains(atomsExtH[i]);
    }
    public boolean isExtAll(int i) {
        return esvByAtomExtAll[i] != null 
                && esvByAtomExtAll[i].getUnsharedAtoms().contains(atomsExtAll[i]);
    }
    
    public ExtendedVariable exthEsv(int i) {
        return esvByAtomExtH[i];
    }
    public ExtendedVariable extallEsv(int i) {
        return esvByAtomExtAll[i];
    }
    
    public int exthEsvId(int i) {
        return (esvByAtomExtH[i] != null) ? esvByAtomExtH[i].index : -1;
    }
    public int extallEsvId(int i) {
        return (esvByAtomExtAll[i] != null) ? esvByAtomExtAll[i].index : -1;
    }
    
    public double exthLambda(int i) {
        return (isExtH(i)) ? esvByAtomExtH[i].getLambda() : 1.0;
    }
    public double extallLambda(int i) {
        return (isExtAll(i)) ? esvByAtomExtAll[i].getLambda() : 1.0;
    }
    
    public double exthLambdaSwitch(int i) {
        return (isExtH(i)) ? esvByAtomExtH[i].getLambdaSwitch() : 1.0;
    }
    public double extallLambdaSwitch(int i) {
        return (isExtAll(i)) ? esvByAtomExtAll[i].getLambdaSwitch() : 1.0;
    }
    
    public Atom[] getAtomsExtH() {
        return atomsExtH;
    }    
    public int[] getMoleculeExtH() {
        return moleculeExtH;
    }
    
    public Atom[] getAtomsExtAll() {
        return atomsExtAll;
    }
    public int[] getMoleculeExtAll() {
        return moleculeExtAll;
    }
    
    public void setLambda(int esvID, double lambda) {
        esvList.get(esvID).setLambda(lambda);
        updateListeners();
    }
    
    public void updateListeners() {
        if (esvScaleBonded) {
            updateBondedEsvLambda();
        }
        if (esvUseVdw) {
            vdw.updateEsvLambda();
        }
        if (esvUsePme) {
            pme.updateEsvLambda();
        }
    }

    public int n() {
        return esvList.size();
    }
    
    public void setEsvBiasTerm(boolean set) {
        esvBiasTerm = set;
    }

    /**
     * Get ESV biases such as discretization, pH, etc.
     * This method public and final for error-checking; new ESVs should override biasEnergy().
     */
    public final double getBiasEnergy(Double temperature) {
        if (!esvBiasTerm) {
            return 0.0;
        }
        if (!esvTerm) {
            logger.warning("Called ExtendedSystem energy while !esvTerm.");
            return 0.0;
        }
        if (esvList == null || esvList.isEmpty()) {
            logger.warning("Called for extended energy with null/empty esvList.");
            return 0.0;
        }
        if (esvTempOverride) {
            temperature = ExtConstants.roomTemperature;
        } else if (temperature == null) {
//            logger.warning("Called for extended bias energy without temperature.");
            temperature = propagationTemperature;
        }
        return biasEnergy(temperature);
    }
    
    /**
     * Get the 
     * @param temperature
     * @return 
     */
    private double biasEnergy(double temperature) {
        double biasEnergySum = 0.0;
        for (ExtendedVariable esv : this) {
            biasEnergySum += esv.getTotalBias(temperature, false);
        }
        return biasEnergySum;
    }

    /**
     * Update the position of all ESV particles.
     */
    public void propagateESVs(Double temperature, double dt, double currentTimePs) {
        if (esvTempOverride) {
            temperature = ExtConstants.roomTemperature;
        } else if (temperature == null) {
            temperature = propagationTemperature;
        } else {
            propagationTemperature = temperature;
        }
        double[] dedl = getdEdL(temperature, false, false);
        if (esvList != null && !esvList.isEmpty()) {
            for (ExtendedVariable esv : esvList) {
                double oldLambda = esv.getLambda();
                esv.propagate(dedl[esv.index], dt, temperature);
                double newLambda = esv.getLambda();
                logger.info(format(" Propagating ESV[%d]: %g --> %g @ psec,temp,bias: %g %g %.2f",
                        esv.index, oldLambda, newLambda,
                        currentTimePs, temperature, esv.getTotalBias(temperature, false)));
            }
            if (esvUseVdw) {
                vdw.updateEsvLambda();
            }
            if (esvUsePme) {
                pme.updateEsvLambda();
            }
        }
    }
    
    /**
     *
     * @param esv
     */
    public void addVariable(ExtendedVariable esv) {
        logf(" ExtendedSystem acquired ESV: %s", esv);
        if (esvList == null) {
            esvList = new ArrayList<>();
        }
        if (!esv.isReady()) {
            esv.readyup();
        }
        esvList.add(esv);
        numESVs = esvList.size();
        esvTerm = true;
        if (esv instanceof TitrationESV) {
            phTerm = true;
        } else {
            logger.warning("Debug?");
        }
        
//        foregroundExtH.addAll(esv.getAtomList(AtomList.PMEVDW_ONE));
        backgroundExtH.addAll(esv.getAtomList(AtomList.PMEVDW_ZRO));
        nAtomsExtH = foregroundExtH.size() + backgroundExtH.size();
        atomsExtH = new Atom[nAtomsExtH];
        moleculeExtH = new int[nAtomsExtH];
        
//        foregroundExtAll.addAll(esv.getAtomList(AtomList.BONDED_ONE));
        backgroundExtAll.addAll(esv.getAtomList(AtomList.BONDED_ZRO));
        nAtomsExtAll = foregroundExtAll.size() + backgroundExtAll.size();
        atomsExtAll = new Atom[nAtomsExtAll];
        moleculeExtAll = new int[nAtomsExtAll];
        
        SB.logfn(" ** ExtH **");
        SB.logfn(" atoms,extAtoms,total: %d %d %d", foregroundExtH.size(), backgroundExtH.size(), nAtomsExtH);
        int numForegroundExtH = foregroundExtH.size();
        for (int i = 0; i < nAtomsExtH; i++) {
            if (i < numForegroundExtH) {
                atomsExtH[i] = foregroundExtH.get(i);
            } else {
                if (i == numForegroundExtH) {
                    SB.logfn(" -- AtomsZro -- ");
                }
                atomsExtH[i] = backgroundExtH.get(i - numForegroundExtH);
            }
            moleculeExtH[i] = atomsExtH[i].getMoleculeNumber();
            SB.logfn(" %s", atomsExtH[i]);
        }
        SB.printIf(false);
        
        SB.logfn(" ** ExtAll **");
        SB.logfn(" atoms,extAtoms,total: %d %d %d", foregroundExtAll.size(), backgroundExtAll.size(), nAtomsExtAll);
        int numForegroundExtAll = foregroundExtAll.size();
        for (int i = 0; i < nAtomsExtAll; i++) {
            if (i < numForegroundExtAll) {
                atomsExtAll[i] = foregroundExtAll.get(i);
            } else {
                if (i == numForegroundExtAll) {
                    SB.logfn(" -- AtomsZro -- ");
                }
                atomsExtAll[i] = backgroundExtAll.get(i - numForegroundExtAll);
            }
            moleculeExtAll[i] = atomsExtAll[i].getMoleculeNumber();
            SB.logfn(" %s", atomsExtAll[i]);
        }
        SB.printIf(false);
        
        if (esvByAtomExtH == null || esvByAtomExtH.length < nAtomsExtH) {
            esvByAtomExtH = new ExtendedVariable[nAtomsExtH];
        }
        for (int i = 0; i < nAtomsExtH; i++) {
            esvByAtomExtH[i] = atomsExtH[i].getEsv();
        }
        if (esvByAtomExtAll == null || esvByAtomExtAll.length < nAtomsExtAll) {
            esvByAtomExtAll = new ExtendedVariable[nAtomsExtAll];
        }
        for (int i = 0; i < nAtomsExtAll; i++) {
            esvByAtomExtAll[i] = atomsExtAll[i].getEsv();
        }
        
        updateListeners();
    }

    /**
     * @return [numESVs] gradient w.r.t. each ESV
     */
    public double[] getdEdL(Double temperature, boolean lambdaBondedTerms, boolean print) {
        double esvDeriv[] = new double[numESVs];
        for (int i = 0; i < numESVs; i++) {
            esvDeriv[i] = getdEdL(i, temperature, lambdaBondedTerms, print);
        }
        return esvDeriv;
    }
    
    public double[] getdEdL() {
        return getdEdL(null, false, false);
    }
    
    public double getdEdL(int esvID) {
        return getdEdL(esvID, null, false, false);
    }
    
    public double getdEdL(int esvID, boolean print) {
        return getdEdL(esvID, null, false, print);
    }
    
    public double getdVdwdL(int esvID) {
        return (esvUseVdw) ? vdw.getdEdEsv(esvID) : 0.0;
    }
    
    public double getdEdL(int esvID, Double temperature, boolean lambdaBondedTerms, boolean print) {
        if (esvTempOverride) {
            temperature = ExtConstants.roomTemperature;
        } else if (temperature == null) {
            temperature = propagationTemperature;
        }
        ExtendedVariable esv = esvList.get(esvID);
        double esvDeriv = 0.0;
        SB.logfn(" %s derivative components: ", esv.getName());
        if (esvBiasTerm) {
            esvDeriv += esv.getTotalBiasDeriv(temperature, print);
        }
        if (esvUseVdw) {
            double dVdw = vdw.getdEdEsv(esvID);
            SB.logfn("  vdW    %d: %g", esvID, dVdw);
            esvDeriv += dVdw;
        }
        if (esvUsePme) {
            double dPme = pme.getdEdEsv(esvID);
            SB.logfn("  PME    %d: %g", esvID, dPme);
            esvDeriv += dPme;
        }
        if (esvScaleBonded) {
            double dBonded = esv.getBondedDeriv();
            esvDeriv += dBonded;
            if (print && esvDecomposeBonded) {
                // Decompose the bonded derivative into term types.
                SB.logfn("  Bonded %d: %g", esvID, dBonded);
                // Foreground portion:
                double fgSum = 0.0;
                HashMap<Class<? extends BondedTerm>,SharedDouble> fgMap = 
                        esv.getBondedDerivDecomp();
                for (SharedDouble dub : fgMap.values()) {
                    fgSum += dub.get();
                }
                SB.logf("    Foreground: %9.6f" , fgSum);
                for (Class<? extends BondedTerm> clas : fgMap.keySet()) {
                    SB.logf("\n      %-16s %9.6f", 
                            clas.getName().replaceAll("ffx.potential.bonded.", "") + ":",
                            fgMap.get(clas).get());
                }
                // Background portion:
                double bgSum = 0.0;
                HashMap<Class<? extends BondedTerm>,SharedDouble> bgMap = 
                        esv.getBackgroundBondedDerivDecomp();
                for (SharedDouble dub : bgMap.values()) {
                    bgSum += dub.get();
                }
                SB.logf("\n    Background: %9.6f" , bgSum);                
                for (Class<? extends BondedTerm> clas : bgMap.keySet()) {
                    SB.logf("\n      %-16s %9.6f", 
                            clas.getName().replaceAll("ffx.potential.bonded.", "") + ":",
                            bgMap.get(clas).get());
                }
            }
        }
        SB.printIf(print);
        return esvDeriv;
    }

    public String getBiasDecomposition() {
        if (!esvBiasTerm) {
            return "";
        }
        double discrBias = 0.0;
        double phBias = 0.0;
        for (ExtendedVariable esv : esvList) {
            discrBias += esv.getDiscrBias();
            if (esv instanceof TitrationESV) {
                phBias += ((TitrationESV) esv).getPhBias(propagationTemperature);
            }
        }
        return  format("    %16s %16.8f\n", "Discretization", discrBias) +
                format("    %16s %16.8f\n", "pH Electrostat", phBias);
    }

    public String getLambdaList() {
        StringBuilder sb = new StringBuilder();
        sb.append(format("Lambdas (%d):", esvList.size()));
        for (int i = 0; i < numESVs; i++) {
            sb.append(format(" %4.2f->%4.2f", 
                    esvList.get(i).getLambda(), esvList.get(i).getLambdaSwitch()));
        }
        return sb.toString();
    }
    
    /**
     * Parallel Java constructs in ForceFieldEnergy, VanDerWaals, and ParticleMeshEwald
     * loop over atom indices. This maps the range {0,nAtoms} to {0,numESVs} so 
     * that parallelization of ESVs can be done in the same constructs.
     */
    public int parallelRangeConversion(int index) {
        return (int) Math.floor((index / (double) nAtomsExtH) * numESVs);
    }
    
    public void updateBondedEsvLambda() {
        for (ExtendedVariable esv : this) {
            esv.updateBondedLambdas();
        }
    }

    /**
     * Allows simple iteration over ESV via "for (ExtendedVariable : ExtendedSystem)".
     */
    @Override
    public Iterator<ExtendedVariable> iterator() {
        return esvList.iterator();
    }
}
