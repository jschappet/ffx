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
package ffx.autoparm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;

import static org.apache.commons.math3.util.FastMath.abs;

import ffx.potential.parameters.AngleType;
import ffx.potential.parameters.AngleType.AngleFunction;
import ffx.potential.parameters.AtomType;
import ffx.potential.parameters.BioType;
import ffx.potential.parameters.BondType;
import ffx.potential.parameters.BondType.BondFunction;
import ffx.potential.parameters.ChargeType;
import ffx.potential.parameters.ForceField;
import ffx.potential.parameters.ForceField.ForceFieldBoolean;
import ffx.potential.parameters.ForceField.ForceFieldDouble;
import ffx.potential.parameters.ForceField.ForceFieldInteger;
import ffx.potential.parameters.ForceField.ForceFieldName;
import ffx.potential.parameters.ForceField.ForceFieldString;
import ffx.potential.parameters.ForceField.ForceFieldType;
import ffx.potential.parameters.MultipoleType;
import ffx.potential.parameters.OutOfPlaneBendType;
import ffx.potential.parameters.PiTorsionType;
import ffx.potential.parameters.PolarizeType;
import ffx.potential.parameters.StretchBendType;
import ffx.potential.parameters.TorsionTorsionType;
import ffx.potential.parameters.TorsionType;
import ffx.potential.parameters.UreyBradleyType;
import ffx.potential.parameters.VDWType;

/*
 * @author Gaurav Chattree and Michael J. Schnieders
 *
 * @since 1.0
 *
 */
public class ForceFieldFilter_2 {

    private static final Logger logger = Logger.getLogger(ForceFieldFilter_2.class.getName());
    private ForceField forceField = null;
    private CompositeConfiguration properties;
    private File forceFieldFile;

    /**
     * <p>
     * Constructor for ForceFieldFilter_2.</p>
     *
     * @param properties a
     * {@link org.apache.commons.configuration.CompositeConfiguration} object.
     * @param forceFieldFile a {@link java.io.File} object.
     */
    public ForceFieldFilter_2(CompositeConfiguration properties, File forceFieldFile) {
        forceField = new ForceField(properties, forceFieldFile);
        this.properties = properties;
        this.forceFieldFile = forceFieldFile;
    }

    /**
     * <p>
     * parseParameterLocation</p>
     *
     * @param parameterLocation a {@link java.lang.String} object.
     * @param keyFile a {@link java.io.File} object.
     * @return a {@link java.io.File} object.
     */
    public static File parseParameterLocation(String parameterLocation, File keyFile) {
        File parameterFile = null;
        if (parameterLocation != null && !parameterLocation.equalsIgnoreCase("NONE")) {
            // Remove quotes
            parameterLocation = parameterLocation.replaceAll("\"", "");
            // Append the suffix if necessary
            /*
             if (!parameterLocation.endsWith(".prm")) {
             parameterLocation = parameterLocation + ".prm";
             } */
            parameterFile = new File(parameterLocation);
            // If the location is not absolute, check if it is relative
            // to the key file location.
            if (!parameterFile.exists() && keyFile != null) {
                parameterFile = new File(keyFile.getParent() + File.separator + parameterLocation);
            }
        }
        return parameterFile;
    }

    /**
     * <p>
     * parse</p>
     *
     * @return a {@link ffx.potential.parameters.ForceField} object.
     */
    public ForceField parse() {
        try {
            /**
             * Parse an external (ie. not in the FFX jar) parameter file.
             */
            if (forceFieldFile != null && forceFieldFile.exists()
                    && forceFieldFile.canRead()) {
                parse(new FileInputStream(forceFieldFile));
                /**
                 * Parse an internal parameter file and add it to the composite
                 * configuration.
                 */
            } else {
                String forceFieldString = properties.getString("forcefield", "AMOEBA-BIO-2009");
                ForceFieldName ff = null;
                try {
                    ff = ForceField.ForceFieldName.valueOf(forceFieldString.toUpperCase().replace('-', '_'));
                } catch (Exception e) {
                    ff = ForceField.ForceFieldName.AMOEBA_BIO_2009;
                }
                URL url = ForceField.getForceFieldURL(ff);
                if (url != null) {
                    try {
                        PropertiesConfiguration config = new PropertiesConfiguration(url);
                        properties.addConfiguration(config);
                    } catch (Exception e) {
                        logger.warning(e.toString());
                    }
                }
            }
            /**
             * Overwrite parameters of the forceFieldFile with those from the
             * CompositeConfiguration.
             */
            if (properties != null) {
                parse(properties);
            }
            //forceField.checkPolarizationTypes();
        } catch (Exception e) {
            String message = "Exception parsing force field.";
            logger.log(Level.WARNING, message, e);
        }
        return forceField;
    }

    private void parse(CompositeConfiguration properties) {
        try {
            int numConfigs = properties.getNumberOfConfigurations();
            /**
             * Loop over the configurations starting with lowest precedence.
             * This way higher precedence entries will overwrite lower
             * precedence entries within the ForceField instance.
             */
            for (int n = numConfigs - 1; n >= 0; n--) {
                Configuration config = properties.getConfiguration(n);
                Iterator i = config.getKeys();
                while (i.hasNext()) {
                    String key = (String) i.next();
                    ForceFieldType type = null;
                    try {
                        type = ForceFieldType.valueOf(key.toUpperCase());
                    } catch (Exception e) {
                        continue;
                    }
                    String list[] = config.getStringArray(key);
                    for (String s : list) {
                        // Add back the key to the input line.
                        s = key + " " + s;
                        String tokens[] = s.trim().split(" +");
                        String input = s;
                        switch (type) {
                            case ATOM:
                                parseAtom(input, tokens);
                                break;
                            case ANGLE:
                                parseAngle(input, tokens);
                                break;
                            case BIOTYPE:
                                parseBioType(input, tokens);
                                break;
                            case BOND:
                                parseBond(input, tokens);
                                break;
                            case CHARGE:
                                parseCharge(input, tokens);
                                break;
                            case MULTIPOLE:
                                parseMultipole(input, tokens);
                                break;
                            case OPBEND:
                                parseOPBend(input, tokens);
                                break;
                            case STRBND:
                                parseStrBnd(input, tokens);
                                break;
                            case PITORS:
                                parsePiTorsion(input, tokens);
                                break;
                            case TORSION:
                                parseTorsion(input, tokens);
                                break;
                            case TORTORS:
                                parseTorsionTorsion(input, tokens);
                                break;
                            case UREYBRAD:
                                parseUreyBradley(input, tokens);
                                break;
                            case VDW:
                                parseVDW(input, tokens);
                                break;
                            case POLARIZE:
                                parsePolarize(input, tokens);
                                break;
                            default:
                                logger.warning("ForceField type recognized, but not stored:" + type);
                        }
                    }
                }
            }
            //forceField.checkPolarizationTypes();
        } catch (Exception e) {
            String message = "Exception parsing force field.";
            logger.log(Level.WARNING, message, e);
        }
    }

    private void parse(InputStream stream) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            while (br.ready()) {
                String input = br.readLine();
                String tokens[] = input.trim().split(" +");
                if (tokens != null) {
                    String keyword = tokens[0].toUpperCase().replaceAll("-",
                            "_");
                    boolean parsed = true;
                    try {
                        // Parse Keywords with a String value.
                        ForceFieldString ffString = ForceFieldString.valueOf(keyword);
                        forceField.addForceFieldString(ffString, tokens[1]);
                    } catch (Exception e) {
                        try {
                            // Parse Keywords with a Double value.
                            ForceFieldDouble ffDouble = ForceFieldDouble.valueOf(keyword);
                            double value = Double.parseDouble(tokens[1]);
                            forceField.addForceFieldDouble(ffDouble, value);
                        } catch (Exception e2) {
                            try {
                                // Parse Keywords with an Integer value.
                                ForceFieldInteger ffInteger = ForceFieldInteger.valueOf(keyword);
                                int value = Integer.parseInt(tokens[1]);
                                forceField.addForceFieldInteger(ffInteger, value);
                            } catch (Exception e3) {
                                try {
                                    // Parse Keywords with an Integer value.
                                    ForceFieldBoolean ffBoolean = ForceFieldBoolean.valueOf(keyword);
                                    boolean value = true;
                                    if (tokens.length > 1 && tokens[0].toUpperCase().endsWith("TERM")) {
                                        /**
                                         * Handle the token "ONLY" specially to
                                         * shut off all other terms.
                                         */
                                        if (tokens[1].equalsIgnoreCase("ONLY")) {
                                            for (ForceFieldBoolean term : ForceFieldBoolean.values()) {
                                                if (term.toString().toUpperCase().endsWith("TERM")) {
                                                    forceField.addForceFieldBoolean(term, false);
                                                }
                                            }
                                        } else if (tokens[1].equalsIgnoreCase("NONE")) {
                                            /**
                                             * Legacy support for the "NONE"
                                             * token.
                                             */
                                            value = false;
                                        } else {
                                            value = Boolean.parseBoolean(tokens[1]);
                                        }
                                    }
                                    forceField.addForceFieldBoolean(ffBoolean, value);
                                    forceField.log(keyword);
                                } catch (Exception e4) {
                                    parsed = false;
                                }
                            }
                        }
                    }
                    if (!parsed) {
                        try {
                            ForceFieldType type = ForceFieldType.valueOf(tokens[0].toUpperCase());
                            switch (type) {
                                case ATOM:
                                    parseAtom(input, tokens);
                                    break;
                                case ANGLE:
                                    parseAngle(input, tokens);
                                    break;
                                case BIOTYPE:
                                    parseBioType(input, tokens);
                                    break;
                                case BOND:
                                    parseBond(input, tokens);
                                    break;
                                case CHARGE:
                                    parseCharge(input, tokens);
                                    break;
                                case MULTIPOLE:
                                    parseMultipole(input, tokens, br);
                                    break;
                                case OPBEND:
                                    parseOPBend(input, tokens);
                                    break;
                                case STRBND:
                                    parseStrBnd(input, tokens);
                                    break;
                                case PITORS:
                                    parsePiTorsion(input, tokens);
                                    break;
                                case TORSION:
                                    parseTorsion(input, tokens);
                                    break;
                                case TORTORS:
                                    parseTorsionTorsion(input, tokens, br);
                                    break;
                                case UREYBRAD:
                                    parseUreyBradley(input, tokens);
                                    break;
                                case VDW:
                                    parseVDW(input, tokens);
                                    break;
                                case POLARIZE:
                                    parsePolarize(input, tokens);
                                    break;
                                default:
                                    logger.warning("ForceField type recognized, but not stored:" + type);
                            }
                            continue;
                        } catch (Exception e) {
                            //String message = "Exception parsing force field parametesr.\n";
                            //logger.log(Level.WARNING, message, e);
                        }
                    }
                }
            }
            br.close();
        } catch (Exception e) {
            String message = "Error parsing force field parameters.\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseAngle(String input, String tokens[]) {
        if (tokens.length < 6) {
            logger.warning("Invalid ANGLE type:\n" + input);
            return;
        }
        int atomClasses[] = new int[3];
        double forceConstant = 0.0;
        int angles = 0;
        double bondAngle[] = null;
        try {
            atomClasses[0] = Integer.parseInt(tokens[1]);
            atomClasses[1] = Integer.parseInt(tokens[2]);
            atomClasses[2] = Integer.parseInt(tokens[3]);
            forceConstant = Double.parseDouble(tokens[4]);
            angles = tokens.length - 5;
            bondAngle = new double[angles];
            for (int i = 0; i < angles; i++) {
                bondAngle[i] = Double.parseDouble(tokens[5 + i]);
            }
        } catch (Exception e) {
            String message = "Exception parsing ANGLE type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
        double newBondAngle[] = new double[angles];
        for (int j = 0; j < angles; j++) {
            newBondAngle[j] = bondAngle[j];
        }
        AngleType angleType = new AngleType(atomClasses, forceConstant,
                newBondAngle, AngleFunction.SEXTIC);
        forceField.addForceFieldType(angleType);
    }

    private void parseAtom(String input, String[] tokens) {
        if (tokens.length < 7) {
            logger.warning("Invalid ATOM type:\n" + input);
            return;
        }
        try {
            int index = 1;
            // Atom Type
            int type = Integer.parseInt(tokens[index++]);
            // Atom Class
            int atomClass = -1;
            // The following try/catch is a nasty hack to check for one of the
            // the following two cases:
            //
            // NUMBER TYPE CLASS IDENTIFIER ... (example is OPLSAA)
            // vs.
            // NUMBER TYPE IDENTIFIER ... (example is OPLSUA)
            //
            // If there is no atom class, a harmless exception will be caught
            // and the atomClass field will remain equal to null.
            try {
                atomClass = Integer.parseInt(tokens[index]);
                // If the parseInt succeeds, this force field has atom classes.
                index++;
            } catch (Exception e) {
                // Some force fields do not use atom classes.
                atomClass = -1;
            }
            // Name
            String name = tokens[index++].intern();
            // The "environment" string may contain spaces,
            // and is therefore surrounded in quotes located at "first" and
            // "last".
            int first = input.indexOf("\"");
            int last = input.lastIndexOf("\"");
            if (first >= last) {
                logger.warning("Invalid ATOM type:\n" + input);
                return;
            }
            // Environment
            String environment = input.substring(first, last + 1).intern();
            // Shrink the tokens array to only include entries
            // after the environment field.
            tokens = input.substring(last + 1).trim().split(" +");
            index = 0;
            // Atomic Number
            int atomicNumber = Integer.parseInt(tokens[index++]);
            // Atomic Mass
            double mass = Double.parseDouble(tokens[index++]);
            // Hybridization
            int hybridization = Integer.parseInt(tokens[index++]);
            AtomType atomType = new AtomType(type, atomClass, name,
                    environment, atomicNumber, mass, hybridization);
            forceField.addForceFieldType(atomType);
        } catch (Exception e) {
            String message = "Exception parsing CHARGE type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseBioType(String input, String tokens[]) {
        if (tokens.length < 5) {
            logger.warning("Invalid BIOTYPE type:\n" + input);
            return;
        }
        try {
            int index = Integer.parseInt(tokens[1]);
            String atomName = tokens[2];
            // The "residue" string may contain spaces,
            // and is therefore surrounded in quotes located at "first" and
            // "last".
            int first = input.indexOf("\"");
            int last = input.lastIndexOf("\"");
            if (first >= last) {
                logger.warning("Invalid BIOTYPE type:\n" + input);
                return;
            }
            // Environment
            String moleculeName = input.substring(first, last + 1).intern();
            // Shrink the tokens array to only include entries
            // after the environment field.
            tokens = input.substring(last + 1).trim().split(" +");
            int atomType = Integer.parseInt(tokens[0]);
            int bondCount = tokens.length - 1;
            String bonds[] = null;
            if (bondCount > 0) {
                bonds = new String[bondCount];
                for (int i = 0; i < bondCount; i++) {
                    bonds[i] = tokens[i + 1];
                }
            }
            BioType bioType = new BioType(index, atomName, moleculeName, atomType, bonds);
            forceField.addForceFieldType(bioType);
        } catch (Exception e) {
            String message = "Exception parsing BIOTYPE type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseBond(String input, String[] tokens) {
        if (tokens.length < 5) {
            logger.warning("Invalid BOND type:\n" + input);
            return;
        }
        try {
            int atomClasses[] = new int[2];
            atomClasses[0] = Integer.parseInt(tokens[1]);
            atomClasses[1] = Integer.parseInt(tokens[2]);
            double forceConstant = Double.parseDouble(tokens[3]);
            double distance = Double.parseDouble(tokens[4]);
            BondType bondType = new BondType(atomClasses, forceConstant,
                    distance, BondFunction.QUARTIC);
            forceField.addForceFieldType(bondType);
        } catch (Exception e) {
            String message = "Exception parsing BOND type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseCharge(String input, String tokens[]) {
        if (tokens.length < 3) {
            logger.warning("Invalid CHARGE type:\n" + input);
            return;
        }
        try {
            int atomType = Integer.parseInt(tokens[1]);
            double partialCharge = Double.parseDouble(tokens[2]);
            ChargeType chargeType = new ChargeType(atomType, partialCharge);
            forceField.addForceFieldType(chargeType);
        } catch (Exception e) {
            String message = "Exception parsing CHARGE type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseMultipole(String input, String[] tokens, BufferedReader br) {
        if (tokens.length < 5) {
            logger.warning("Invalid MULTIPOLE type:\n" + input);
            return;
        }
        try {
            int numTypes = tokens.length - 2;
            int atomTypes[] = new int[numTypes];
            for (int i = 0; i < numTypes; i++) {
                atomTypes[i] = Integer.parseInt(tokens[i + 1]);
            }
            MultipoleType.MultipoleFrameDefinition frameDefinition
                    = MultipoleType.MultipoleFrameDefinition.ZTHENX;
            if (atomTypes.length == 3 && (atomTypes[1] < 0 || atomTypes[2] < 0)) {
                frameDefinition = MultipoleType.MultipoleFrameDefinition.BISECTOR;
            } else if (atomTypes.length == 4 && atomTypes[2] < 0 && atomTypes[3] < 0) {
                if (atomTypes[1] < 0) {
                    frameDefinition = MultipoleType.MultipoleFrameDefinition.TRISECTOR;
                } else {
                    frameDefinition = MultipoleType.MultipoleFrameDefinition.ZTHENBISECTOR;
                }
            }
            for (int i = 0; i < numTypes; i++) {
                atomTypes[i] = abs(atomTypes[i]);
            }
            double c = Double.parseDouble(tokens[1 + numTypes]);
            input = br.readLine();
            tokens = input.trim().split(" +");
            if (tokens.length != 3) {
                logger.warning("Invalid MULTIPOLE type:\n" + input);
                return;
            }
            double dipole[] = new double[3];
            dipole[0] = Double.parseDouble(tokens[0]);
            dipole[1] = Double.parseDouble(tokens[1]);
            dipole[2] = Double.parseDouble(tokens[2]);
            input = br.readLine();
            tokens = input.trim().split(" +");
            if (tokens.length != 1) {
                logger.warning("Invalid MULTIPOLE type:\n" + input);
                return;
            }
            double quadrupole[][] = new double[3][3];
            quadrupole[0][0] = Double.parseDouble(tokens[0]);
            input = br.readLine();
            tokens = input.trim().split(" +");
            if (tokens.length != 2) {
                logger.warning("Invalid MULTIPOLE type:\n" + input);
                return;
            }
            quadrupole[1][0] = Double.parseDouble(tokens[0]);
            quadrupole[1][1] = Double.parseDouble(tokens[1]);
            input = br.readLine();
            tokens = input.trim().split(" +");
            if (tokens.length != 3) {
                logger.warning("Invalid MULTIPOLE type:\n" + input);
                return;
            }
            quadrupole[2][0] = Double.parseDouble(tokens[0]);
            quadrupole[2][1] = Double.parseDouble(tokens[1]);
            quadrupole[2][2] = Double.parseDouble(tokens[2]);
            // Fill in symmetric components.
            quadrupole[0][1] = quadrupole[1][0];
            quadrupole[0][2] = quadrupole[2][0];
            quadrupole[1][2] = quadrupole[2][1];
            MultipoleType multipoleType = new MultipoleType(c, dipole,
                    quadrupole, atomTypes, frameDefinition);
            forceField.addForceFieldType(multipoleType);
        } catch (Exception e) {
            String message = "Exception parsing MULTIPOLE type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    /**
     * Parse a single line multipole.
     *
     * @param input
     * @param tokens
     *
     * @since 1.0
     */
    private void parseMultipole(String input, String[] tokens) {
        if (tokens.length < 14) {
            logger.warning("Invalid MULTIPOLE type:" + Arrays.toString(tokens));
            return;
        }
        try {
            int numTypes = tokens.length - 11;
            int atomTypes[] = new int[numTypes];
            for (int i = 0; i < numTypes; i++) {
                atomTypes[i] = Integer.parseInt(tokens[i + 1]);
            }
            MultipoleType.MultipoleFrameDefinition frameDefinition = MultipoleType.MultipoleFrameDefinition.ZTHENX;
            if (atomTypes.length == 3 && (atomTypes[1] < 0 || atomTypes[2] < 0)) {
                frameDefinition = MultipoleType.MultipoleFrameDefinition.BISECTOR;
            } else if (atomTypes.length == 4 && atomTypes[2] < 0 && atomTypes[3] < 0) {
                if (atomTypes[1] < 0) {
                    frameDefinition = MultipoleType.MultipoleFrameDefinition.TRISECTOR;
                } else {
                    frameDefinition = MultipoleType.MultipoleFrameDefinition.ZTHENBISECTOR;
                }
            }
            for (int i = 0; i < numTypes; i++) {
                atomTypes[i] = abs(atomTypes[i]);
            }
            double dipole[] = new double[3];
            double quadrupole[][] = new double[3][3];
            double c = new Double(tokens[1 + numTypes]);
            dipole[0] = new Double(tokens[2 + numTypes]);
            dipole[1] = new Double(tokens[3 + numTypes]);
            dipole[2] = new Double(tokens[4 + numTypes]);
            quadrupole[0][0] = new Double(tokens[5 + numTypes]);
            quadrupole[1][0] = new Double(tokens[6 + numTypes]);
            quadrupole[1][1] = new Double(tokens[7 + numTypes]);
            quadrupole[2][0] = new Double(tokens[8 + numTypes]);
            quadrupole[2][1] = new Double(tokens[9 + numTypes]);
            quadrupole[2][2] = new Double(tokens[10 + numTypes]);
            // Fill in symmetric components.
            quadrupole[0][1] = quadrupole[1][0];
            quadrupole[0][2] = quadrupole[2][0];
            quadrupole[1][2] = quadrupole[2][1];
            MultipoleType multipoleType = new MultipoleType(c, dipole,
                    quadrupole, atomTypes, frameDefinition);
            forceField.addForceFieldType(multipoleType);
        } catch (Exception e) {
            String message = "Exception parsing MULTIPOLE type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseOPBend(String input, String[] tokens) {
        if (tokens.length < 6) {
            logger.warning("Invalid OPBEND type:\n" + input);
            return;
        }
        try {
            int atomClasses[] = new int[4];
            atomClasses[0] = Integer.parseInt(tokens[1]);
            atomClasses[1] = Integer.parseInt(tokens[2]);
            atomClasses[2] = Integer.parseInt(tokens[3]);
            atomClasses[3] = Integer.parseInt(tokens[4]);
            double forceConstant = Double.parseDouble(tokens[5]);
            OutOfPlaneBendType opbendType = new OutOfPlaneBendType(atomClasses,
                    forceConstant);
            forceField.addForceFieldType(opbendType);
        } catch (Exception e) {
            String message = "Exception parsing OPBEND type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parsePiTorsion(String input, String[] tokens) {
        if (tokens.length < 4) {
            logger.warning("Invalid PITORS type:\n" + input);
            return;
        }
        try {
            int atomClasses[] = new int[2];
            atomClasses[0] = Integer.parseInt(tokens[1]);
            atomClasses[1] = Integer.parseInt(tokens[2]);
            double forceConstant = Double.parseDouble(tokens[3]);
            PiTorsionType piTorsionType = new PiTorsionType(atomClasses,
                    forceConstant);
            forceField.addForceFieldType(piTorsionType);
        } catch (Exception e) {
            String message = "Exception parsing PITORS type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parsePolarize(String input, String tokens[]) {
        if (tokens.length < 4) {
            logger.warning("Invalid POLARIZE type:\n" + input);
        }
        try {
            int atomType = Integer.parseInt(tokens[1]);
            double polarizability = Double.parseDouble(tokens[2]);
            double thole = Double.parseDouble(tokens[3]);
            int entries = tokens.length - 4;
            int polarizationGroup[] = null;
            if (entries > 0) {
                polarizationGroup = new int[entries];
                for (int i = 4; i < tokens.length; i++) {
                    polarizationGroup[i - 4] = Integer.parseInt(tokens[i]);
                }
            }
            PolarizeType polarizeType = new PolarizeType(atomType,
                    polarizability, thole, polarizationGroup);
            forceField.addForceFieldType(polarizeType);
            //polarizeType.log();
        } catch (Exception e) {
            String message = "Exception parsing POLARIZE type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseStrBnd(String input, String[] tokens) {
        if (tokens.length < 6) {
            logger.warning("Invalid STRBND type:\n" + input);
            return;
        }
        try {
            int atomClasses[] = new int[3];
            atomClasses[0] = Integer.parseInt(tokens[1]);
            atomClasses[1] = Integer.parseInt(tokens[2]);
            atomClasses[2] = Integer.parseInt(tokens[3]);
            double forceConstants[] = new double[2];
            forceConstants[0] = Double.parseDouble(tokens[4]);
            forceConstants[1] = Double.parseDouble(tokens[5]);
            StretchBendType strbndType = new StretchBendType(atomClasses,
                    forceConstants);
            forceField.addForceFieldType(strbndType);
        } catch (Exception e) {
            String message = "Exception parsing STRBND type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseTorsion(String input, String tokens[]) {
        if (tokens.length < 5) {
            logger.warning("Invalid TORSION type:\n" + input);
            return;
        }
        try {
            int atomClasses[] = new int[4];
            atomClasses[0] = Integer.parseInt(tokens[1]);
            atomClasses[1] = Integer.parseInt(tokens[2]);
            atomClasses[2] = Integer.parseInt(tokens[3]);
            atomClasses[3] = Integer.parseInt(tokens[4]);
            int terms = (tokens.length - 5) / 3;
            double amplitude[] = new double[terms];
            double phase[] = new double[terms];
            int periodicity[] = new int[terms];
            int index = 5;
            for (int i = 0; i < terms; i++) {
                amplitude[i] = Double.parseDouble(tokens[index++]);
                phase[i] = Double.parseDouble(tokens[index++]);
                periodicity[i] = Integer.parseInt(tokens[index++]);
            }
            TorsionType torsionType = new TorsionType(atomClasses, amplitude,
                    phase, periodicity);
            forceField.addForceFieldType(torsionType);
        } catch (Exception e) {
            String message = "Exception parsing TORSION type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseTorsionTorsion(String input, String[] tokens,
            BufferedReader br) {
        if (tokens.length < 8) {
            logger.warning("Invalid TORTORS type:\n" + input);
            return;
        }
        try {
            int atomClasses[] = new int[5];
            for (int i = 0; i < 5; i++) {
                atomClasses[i] = Integer.parseInt(tokens[i + 1]);
            }
            int gridPoints[] = new int[2];
            gridPoints[0] = Integer.parseInt(tokens[6]);
            gridPoints[1] = Integer.parseInt(tokens[7]);
            int points = gridPoints[0] * gridPoints[1];
            double torsion1[] = new double[points];
            double torsion2[] = new double[points];
            double energy[] = new double[points];
            for (int i = 0; i < points; i++) {
                input = br.readLine();
                tokens = input.trim().split(" +");
                if (tokens.length != 3) {
                    logger.warning("Invalid TORTORS type:\n" + input);
                    return;
                }
                torsion1[i] = Double.parseDouble(tokens[0]);
                torsion2[i] = Double.parseDouble(tokens[1]);
                energy[i] = Double.parseDouble(tokens[2]);
            }
            TorsionTorsionType torsionTorsionType = new TorsionTorsionType(
                    atomClasses, gridPoints, torsion1, torsion2, energy);
            forceField.addForceFieldType(torsionTorsionType);
        } catch (Exception e) {
            String message = "Exception parsing TORTORS type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseTorsionTorsion(String input, String[] tokens) {
        if (tokens.length < 8) {
            logger.warning("Invalid TORTORS type:\n" + input);
            return;
        }
        try {
            int atomClasses[] = new int[5];
            for (int i = 0; i < 5; i++) {
                atomClasses[i] = Integer.parseInt(tokens[i + 1]);
            }
            int gridPoints[] = new int[2];
            gridPoints[0] = new Integer(tokens[6]);
            gridPoints[1] = new Integer(tokens[7]);

            int points = gridPoints[0] * gridPoints[1];

            int numTokens = points * 3 + 8;
            if (tokens.length < numTokens) {
                logger.warning("Invalid TORTORS type:\n" + input);
                return;
            }
            double torsion1[] = new double[points];
            double torsion2[] = new double[points];
            double energy[] = new double[points];
            int index = 8;
            for (int i = 0; i < points; i++) {
                torsion1[i] = new Double(tokens[index++]);
                torsion2[i] = new Double(tokens[index++]);
                energy[i] = new Double(tokens[index++]);
            }
            TorsionTorsionType torsionTorsionType = new TorsionTorsionType(
                    atomClasses, gridPoints, torsion1, torsion2, energy);
            forceField.addForceFieldType(torsionTorsionType);
        } catch (Exception e) {
            String message = "Exception parsing TORTORS type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseUreyBradley(String input, String[] tokens) {
        if (tokens.length < 5) {
            logger.warning("Invalid UREYBRAD type:\n" + input);
            return;
        }
        try {
            int atomClasses[] = new int[3];
            atomClasses[0] = Integer.parseInt(tokens[1]);
            atomClasses[1] = Integer.parseInt(tokens[2]);
            atomClasses[2] = Integer.parseInt(tokens[3]);
            double forceConstant = Double.parseDouble(tokens[4]);
            double distance = Double.parseDouble(tokens[5]);
            UreyBradleyType ureyType = new UreyBradleyType(atomClasses,
                    forceConstant, distance);
            forceField.addForceFieldType(ureyType);
        } catch (Exception e) {
            String message = "Exception parsing UREYBRAD type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }

    private void parseVDW(String input, String[] tokens) {
        if (tokens.length < 4) {
            logger.warning("Invalid VDW type:\n" + input);
            return;
        }
        try {
            int atomType = Integer.parseInt(tokens[1]);
            double radius = Double.parseDouble(tokens[2]);
            double wellDepth = Double.parseDouble(tokens[3]);
            double reductionFactor = -1.0;
            if (tokens.length == 5) {
                reductionFactor = Double.parseDouble(tokens[4]);
            }
            VDWType vdwType = new VDWType(atomType, radius, wellDepth,
                    reductionFactor);
            forceField.addForceFieldType(vdwType);
        } catch (Exception e) {
            String message = "Exception parsing VDW type:\n" + input + "\n";
            logger.log(Level.SEVERE, message, e);
        }
    }
}
