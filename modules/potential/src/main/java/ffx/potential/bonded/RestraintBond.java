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
package ffx.potential.bonded;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Geometry;
import javax.media.j3d.LineArray;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Vector3d;

import static org.apache.commons.math3.util.FastMath.pow;

import ffx.crystal.Crystal;
import ffx.numerics.AtomicDoubleArray;
import ffx.potential.bonded.RendererCache.ViewModel;
import ffx.potential.parameters.BondType;

import static ffx.numerics.VectorMath.angle;
import static ffx.numerics.VectorMath.cross;
import static ffx.numerics.VectorMath.diff;
import static ffx.numerics.VectorMath.norm;
import static ffx.numerics.VectorMath.r;
import static ffx.numerics.VectorMath.scalar;
import static ffx.numerics.VectorMath.sum;
import static ffx.potential.parameters.BondType.units;

/**
 * <p>
 * RestraintBond class.</p>
 *
 * @author Michael J. Schnieders
 *
 */
public class RestraintBond extends BondedTerm implements LambdaInterface {

    private static final Logger logger = Logger.getLogger(RestraintBond.class.getName());
    private double lambda = 1.0;
    private double restraintLambda = 1.0;
    private double rL3 = 1.0;
    private double rL2 = 1.0;
    private double rL1 = 1.0;
    private double restraintLambdaStart = 0.75;
    private final double restraintLambdaStop = 1.00;
    private double restraintLambdaWindow = (restraintLambdaStop - restraintLambdaStart);
    private double dEdL = 0.0;
    private double d2EdL2 = 0.0;
    private double dEdXdL[][] = new double[2][3];

    @Override
    public void setLambda(double lambda) {
        this.lambda = lambda;

        if (lambda < restraintLambdaStart) {
            restraintLambda = 1.0;
            rL3 = 1.0;
            rL2 = 0.0;
            rL1 = 0.0;
        } else {
            restraintLambda = 1.0 - (lambda - restraintLambdaStart) / restraintLambdaWindow;
            rL3 = pow(restraintLambda, 3.0);
            rL2 = -3.0 * pow(restraintLambda, 2.0) / restraintLambdaWindow;
            rL1 = 6.0 * restraintLambda / (restraintLambdaWindow * restraintLambdaWindow);
        }

    }

    @Override
    public double getLambda() {
        return lambda;
    }

    @Override
    public double getdEdL() {
        return dEdL;
    }

    @Override
    public double getd2EdL2() {
        return d2EdL2;
    }

    @Override
    public void getdEdXdL(double[] gradient) {
        int i1 = atoms[0].getXYZIndex() - 1;
        int index = i1 * 3;
        gradient[index++] += dEdXdL[0][0];
        gradient[index++] += dEdXdL[0][1];
        gradient[index] += dEdXdL[0][2];
        int i2 = atoms[1].getXYZIndex() - 1;
        index = i2 * 3;
        gradient[index++] += dEdXdL[1][0];
        gradient[index++] += dEdXdL[1][1];
        gradient[index] += dEdXdL[1][2];
    }

    /**
     * Bonding Character
     */
    public enum BondCharacter {

        SINGLEBOND, DOUBLEBOND, TRIPLEBOND;
    }
    private static final long serialVersionUID = 1L;
    /**
     * Length in Angstroms that is added to Atomic Radii when determining if two
     * Atoms are within bonding distance
     */
    public static final float BUFF = 0.7f;
    public BondType bondType = null;
    private double rigidScale = 1.0;
    private static final float a0col[] = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    private static final float f4a[] = {0.0f, 0.0f, 0.0f, 0.9f};
    private static final float f4b[] = {0.0f, 0.0f, 0.0f, 0.9f};
    private static float f16[] = {0.0f, 0.0f, 0.0f, 0.9f, 0.0f, 0.0f, 0.0f,
        0.9f, 0.0f, 0.0f, 0.0f, 0.9f, 0.0f, 0.0f, 0.0f, 0.9f};
    // Some static variables used for computing cylinder orientations
    private static double d;
    private static double a13d[] = new double[3];
    private static double a23d[] = new double[3];
    private static double mid[] = new double[3];
    private static double diff3d[] = new double[3];
    private static double sum3d[] = new double[3];
    private static double coord[] = new double[12];
    private static double y[] = {0.0d, 1.0d, 0.0d};
    private static AxisAngle4d axisAngle = new AxisAngle4d();
    private static double[] bcross = new double[4];
    private static double[] cstart = new double[3];
    private static Vector3d pos3d = new Vector3d();
    private static double angle;
    /**
     * List of Bonds that this Bond forms angles with
     */
    private ArrayList<Bond> formsAngleWith = new ArrayList<Bond>();
    /**
     * *************************************************************************
     */
    // Java3D methods and variables for visualization of this Bond.
    private RendererCache.ViewModel viewModel = RendererCache.ViewModel.INVISIBLE;
    private BranchGroup branchGroup;
    private TransformGroup cy1tg, cy2tg;
    private Transform3D cy1t3d, cy2t3d;
    private Shape3D cy1, cy2;
    private Vector3d scale;
    private int detail = 3;
    private LineArray la;
    private int lineIndex;
    private boolean wireVisible = true;
    private Crystal crystal;

    /**
     * Bond constructor.
     *
     * @param a1 Atom number 1.
     * @param a2 Atom number 2.
     * @param crystal the Crystal defines boundary and symmetry conditions.
     */
    public RestraintBond(Atom a1, Atom a2, Crystal crystal) {
        atoms = new Atom[2];

        this.crystal = crystal;

        int i1 = a1.getXYZIndex();
        int i2 = a2.getXYZIndex();
        if (i1 < i2) {
            atoms[0] = a1;
            atoms[1] = a2;
        } else {
            atoms[0] = a2;
            atoms[1] = a1;
        }
        setID_Key(false);
        viewModel = RendererCache.ViewModel.WIREFRAME;
    }

    /**
     * Set a reference to the force field parameters.
     *
     * @param bondType a {@link ffx.potential.parameters.BondType} object.
     */
    public void setBondType(BondType bondType) {
        this.bondType = bondType;
    }

    /**
     * <p>
     * Setter for the field <code>rigidScale</code>.</p>
     *
     * @param rigidScale a double.
     */
    public void setRigidScale(double rigidScale) {
        this.rigidScale = rigidScale;
    }

    /**
     * Find the other Atom in <b>this</b> Bond. These two atoms are said to be
     * 1-2.
     *
     * @param a The known Atom.
     * @return The other Atom that makes up <b>this</b> Bond, or Null if Atom a
     * is not part of <b>this</b> Bond.
     */
    public Atom get1_2(Atom a) {
        if (a == atoms[0]) {
            return atoms[1];
        }
        if (a == atoms[1]) {
            return atoms[0];
        }
        return null; // Atom not found in bond
    }

    /**
     * Finds the common Atom between <b>this</b> Bond and Bond b.
     *
     * @param b Bond to compare with.
     * @return The Atom the Bonds have in common or Null if they are the same
     * Bond or have no atom in common
     */
    public Atom getCommonAtom(RestraintBond b) {
        if (b == this || b == null) {
            return null;
        }
        if (b.atoms[0] == atoms[0]) {
            return atoms[0];
        }
        if (b.atoms[0] == atoms[1]) {
            return atoms[1];
        }
        if (b.atoms[1] == atoms[0]) {
            return atoms[0];
        }
        if (b.atoms[1] == atoms[1]) {
            return atoms[1];
        }
        return null; // Common atom not found
    }

    /**
     * Find the Atom that <b>this</b> Bond and Bond b do not have in common.
     *
     * @param b Bond to compare with
     * @return The Atom that Bond b and <b>this</b> Bond do not have in common,
     * or Null if they have no Atom in common
     */
    public Atom getOtherAtom(RestraintBond b) {
        if (b == this || b == null) {
            return null;
        }
        if (b.atoms[0] == atoms[0]) {
            return atoms[1];
        }
        if (b.atoms[0] == atoms[1]) {
            return atoms[0];
        }
        if (b.atoms[1] == atoms[0]) {
            return atoms[1];
        }
        if (b.atoms[1] == atoms[1]) {
            return atoms[0];
        }
        return null;
    }

    /**
     * Create the Bond Scenegraph Objects.
     *
     * @param newShapes List
     */
    private void initJ3D(List<BranchGroup> newShapes) {
        detail = RendererCache.detail;
        branchGroup = RendererCache.doubleCylinderFactory(atoms[0], atoms[1],
                detail);
        cy1tg = (TransformGroup) branchGroup.getChild(0);
        cy2tg = (TransformGroup) branchGroup.getChild(1);
        cy1 = (Shape3D) cy1tg.getChild(0);
        cy2 = (Shape3D) cy2tg.getChild(0);
        newShapes.add(branchGroup);
        cy1t3d = RendererCache.transform3DFactory();
        cy2t3d = RendererCache.transform3DFactory();
        update();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeFromParent() {
        super.removeFromParent();
        cy1 = null;
        cy2 = null;
        cy1tg = null;
        cy2tg = null;
        if (cy1t3d != null) {
            RendererCache.poolTransform3D(cy1t3d);
            RendererCache.poolTransform3D(cy2t3d);
            cy1t3d = null;
            cy2t3d = null;
        }
        if (branchGroup != null) {
            branchGroup.detach();
            branchGroup.setUserData(null);
            RendererCache.poolDoubleCylinder(branchGroup);
            branchGroup = null;
        }
    }

    /**
     * <p>
     * sameGroup</p>
     *
     * @return a boolean.
     */
    public boolean sameGroup() {
        if (atoms[0].getParent() == atoms[1].getParent()) {
            return true;
        }
        return false;
    }

    /**
     * <p>
     * setBondTransform3d</p>
     *
     * @param t3d a {@link javax.media.j3d.Transform3D} object.
     * @param pos an array of double.
     * @param orient an array of double.
     * @param len a double.
     * @param newRot a boolean.
     */
    public void setBondTransform3d(Transform3D t3d, double[] pos,
            double[] orient, double len, boolean newRot) {
        // Bond Orientation
        if (newRot) {
            angle = angle(orient, y);
            cross(y, orient, bcross);
            bcross[3] = angle - Math.PI;
            axisAngle.set(bcross);
        }
        // Scale the orientation vector to be a fourth the bond length
        // and add it to the position vector of the of the first atom
        scalar(orient, len / 4.0d, cstart);
        sum(cstart, pos, cstart);
        pos3d.set(cstart);
        t3d.setTranslation(pos3d);
        t3d.setRotation(axisAngle);
        t3d.setScale(scale);
    }

    /**
     * Set the color of this Bond's Java3D shapes based on the passed Atom.
     *
     * @param a Atom
     */
    public void setColor(Atom a) {
        if (viewModel != ViewModel.INVISIBLE && viewModel != ViewModel.WIREFRAME && branchGroup != null) {
            if (a == atoms[0]) {
                cy1.setAppearance(a.getAtomAppearance());
            } else if (a == atoms[1]) {
                cy2.setAppearance(a.getAtomAppearance());
            }
        }
        setWireVisible(wireVisible);
    }

    /**
     * Manage cylinder visibility.
     *
     * @param visible boolean
     * @param newShapes List
     */
    public void setCylinderVisible(boolean visible, List<BranchGroup> newShapes) {
        if (!visible) {
            // Make this Bond invisible.
            if (branchGroup != null) {
                cy1.setPickable(false);
                cy1.setAppearance(RendererCache.nullAp);
                cy2.setPickable(false);
                cy2.setAppearance(RendererCache.nullAp);
                // branchGroup = null;
            }
        } else if (branchGroup == null) {
            // Get Java3D primitives from the RendererCache
            initJ3D(newShapes);
        } else {
            // Scale the cylinders to match the current ViewModel
            cy1t3d.setScale(scale);
            cy1tg.setTransform(cy1t3d);
            cy2t3d.setScale(scale);
            cy2tg.setTransform(cy2t3d);
            cy1.setAppearance(atoms[0].getAtomAppearance());
            cy2.setAppearance(atoms[1].getAtomAppearance());
        }
    }

    /**
     * {@inheritDoc}
     *
     * Polymorphic setView method.
     */
    @Override
    public void setView(RendererCache.ViewModel newViewModel,
            List<BranchGroup> newShapes) {
        switch (newViewModel) {
            case WIREFRAME:
                viewModel = ViewModel.WIREFRAME;
                setWireVisible(true);
                setCylinderVisible(false, newShapes);
                break;
            case SPACEFILL:
            case INVISIBLE:
            case RMIN:
                viewModel = ViewModel.INVISIBLE;
                setWireVisible(false);
                setCylinderVisible(false, newShapes);
                break;
            case RESTRICT:
                if (!atoms[0].isSelected() || !atoms[1].isSelected()) {
                    viewModel = ViewModel.INVISIBLE;
                    setWireVisible(false);
                    setCylinderVisible(false, newShapes);
                }
                break;
            case BALLANDSTICK:
            case TUBE:
                viewModel = newViewModel;
                // Get the radius to use
                double rad;
                double len = getValue() / 2.0d;
                if (viewModel == RendererCache.ViewModel.BALLANDSTICK) {
                    rad = 0.1d * RendererCache.radius;
                } else {
                    rad = 0.2d * RendererCache.radius;
                }
                if (scale == null) {
                    scale = new Vector3d();
                }
                scale.set(rad, len, rad);
                setWireVisible(false);
                setCylinderVisible(true, newShapes);
                break;
            case DETAIL:
                int res = RendererCache.detail;
                if (res != detail) {
                    detail = res;
                    if (branchGroup != null) {
                        Geometry geom1 = RendererCache.getCylinderGeom(0, detail);
                        Geometry geom2 = RendererCache.getCylinderGeom(1, detail);
                        Geometry geom3 = RendererCache.getCylinderGeom(2, detail);
                        cy1.removeAllGeometries();
                        cy2.removeAllGeometries();
                        cy1.addGeometry(geom1);
                        cy1.addGeometry(geom2);
                        cy1.addGeometry(geom3);
                        cy2.addGeometry(geom1);
                        cy2.addGeometry(geom2);
                        cy2.addGeometry(geom3);
                    }
                }
                if (scale == null) {
                    scale = new Vector3d();
                }
                double newRadius;
                if (viewModel == RendererCache.ViewModel.BALLANDSTICK) {
                    newRadius = 0.1d * RendererCache.radius;
                } else if (viewModel == RendererCache.ViewModel.TUBE) {
                    newRadius = 0.2d * RendererCache.radius;
                } else {
                    break;
                }
                if (newRadius != scale.x) {
                    scale.x = newRadius;
                    scale.y = newRadius;
                    if (branchGroup != null) {
                        setView(viewModel, newShapes);
                    }
                }
                break;
            case SHOWHYDROGENS:
                if (atoms[0].getAtomicNumber() == 1 || atoms[1].getAtomicNumber() == 1) {
                    setView(viewModel, newShapes);
                }
                break;
            case HIDEHYDROGENS:
                if (atoms[0].getAtomicNumber() == 1 || atoms[1].getAtomicNumber() == 1) {
                    viewModel = ViewModel.INVISIBLE;
                    setWireVisible(false);
                    setCylinderVisible(false, newShapes);
                }
                break;
            case FILL:
            case POINTS:
            case LINES:
                if (branchGroup != null && viewModel != ViewModel.INVISIBLE) {
                    cy1.setAppearance(atoms[0].getAtomAppearance());
                    cy2.setAppearance(atoms[1].getAtomAppearance());
                }
                break;
        }
    }

    /**
     * <p>
     * setWire</p>
     *
     * @param l a {@link javax.media.j3d.LineArray} object.
     * @param i a int.
     */
    public void setWire(LineArray l, int i) {
        la = l;
        lineIndex = i;
    }

    /**
     * Manage wireframe visibility.
     *
     * @param visible a boolean.
     */
    public void setWireVisible(boolean visible) {
        if (!visible) {
            wireVisible = false;
            la.setColors(lineIndex, a0col);
        } else {
            wireVisible = true;
            float cols[] = f16;
            float col1[] = f4a;
            float col2[] = f4b;
            atoms[0].getAtomColor().get(col1);
            atoms[1].getAtomColor().get(col2);
            for (int i = 0; i < 3; i++) {
                cols[i] = col1[i];
                cols[4 + i] = col1[i];
                cols[8 + i] = col2[i];
                cols[12 + i] = col2[i];
            }
            la.setColors(lineIndex, cols);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Update recomputes the bonds length, Wireframe vertices, and Cylinder
     * Transforms
     */
    @Override
    public void update() {
        // Update the Bond Length
        atoms[0].getXYZ(a13d);
        atoms[1].getXYZ(a23d);
        diff(a13d, a23d, diff3d);
        d = r(diff3d);
        setValue(d);
        sum(a13d, a23d, sum3d);
        scalar(sum3d, 0.5d, mid);
        // Update the Wireframe Model.
        if (la != null) {
            for (int i = 0; i < 3; i++) {
                coord[i] = a13d[i];
                coord[3 + i] = mid[i];
                coord[6 + i] = mid[i];
                coord[9 + i] = a23d[i];
            }
            la.setCoordinates(lineIndex, coord);
        }
        // Update the Bond cylinder transforms.
        if (branchGroup != null) {
            norm(diff3d, diff3d);
            scale.y = d / 2.0d;
            setBondTransform3d(cy1t3d, mid, diff3d, d, true);
            scalar(diff3d, -1.0d, diff3d);
            setBondTransform3d(cy2t3d, mid, diff3d, d, false);
            cy1tg.setTransform(cy1t3d);
            cy2tg.setTransform(cy2t3d);
        }
    }

    /**
     * Evaluate this Bond energy.
     *
     * @param gradient Evaluate the gradient.
     * @param threadID
     * @param gradX
     * @param gradY
     * @param gradZ
     * @return Returns the energy.
     */
    @Override
    public double energy(boolean gradient, int threadID,
            AtomicDoubleArray gradX,
            AtomicDoubleArray gradY,
            AtomicDoubleArray gradZ,
            AtomicDoubleArray lambdaGradX,
            AtomicDoubleArray lambdaGradY,
            AtomicDoubleArray lambdaGradZ) {

        double a0[] = new double[3];
        double a1[] = new double[3];
        /**
         * The vector from Atom 1 to Atom 0.
         */
        double v10[] = new double[3];
        /**
         * Gradient on Atoms 0 & 1.
         */
        double g0[] = new double[3];
        double g1[] = new double[3];

        atoms[0].getXYZ(a0);
        atoms[1].getXYZ(a1);

        diff(a0, a1, v10);

        if (crystal != null) {
            crystal.image(v10);
        }

        value = r(v10);
        double dv = value - bondType.distance;
        double dv2 = dv * dv;
        double kx2 = units * bondType.forceConstant * dv2 * esvLambda;
        energy = rL3 * kx2;
        dEdL = rL2 * kx2;
        d2EdL2 = rL1 * kx2;
        double deddt = 2.0 * units * bondType.forceConstant * dv * esvLambda;
        double de = 0.0;

        if (value > 0.0) {
            de = deddt / value;
        }

        scalar(v10, rL3 * de, g0);
        scalar(v10, -rL3 * de, g1);
        if (gradient) {
            //atoms[0].addToXYZGradient(g0[0], g0[1], g0[2]);
            //atoms[1].addToXYZGradient(g1[0], g1[1], g1[2]);
            int i0 = atoms[0].getXYZIndex() - 1;
            gradX.add(threadID, i0, g0[0]);
            gradY.add(threadID, i0, g0[1]);
            gradZ.add(threadID, i0, g0[2]);
            int i1 = atoms[1].getXYZIndex() - 1;
            gradX.add(threadID, i1, g1[0]);
            gradY.add(threadID, i1, g1[1]);
            gradZ.add(threadID, i1, g1[2]);
        }

        /**
         * Remove the factor of rL3
         */
        scalar(v10, rL2 * de, g0);
        scalar(v10, -rL2 * de, g1);
        dEdXdL[0][0] = g0[0];
        dEdXdL[0][1] = g0[1];
        dEdXdL[0][2] = g0[2];
        dEdXdL[1][0] = g1[0];
        dEdXdL[1][1] = g1[1];
        dEdXdL[1][2] = g1[2];

        value = dv;
        if (esvTerm) {
            setEsvDeriv(energy * dedesvChain / esvLambda);
        }
        return energy;
    }

    /**
     * Log details for this Bond energy term.
     */
    public void log() {
        logger.info(String.format(" %s %6d-%s %6d-%s %6.4f  %6.4f  %10.4f",
                "Restraint-Bond", atoms[0].getXYZIndex(), atoms[0].getAtomType().name,
                atoms[1].getXYZIndex(), atoms[1].getAtomType().name,
                bondType.distance, value, energy));
    }
}
