// TEST ATOMIC COORDINATE GRADIENT

import ffx.potential.ForceFieldEnergy;
import ffx.potential.bonded.Atom;
import ffx.potential.bonded.MolecularAssembly;

int nargs = args.size();

// Name of the file (PDB or XYZ).
String filename = args[0];
if (filename == null) {
    println("Usage: ffx testGradient filename fdstep verbose");
    return;
}

// Finite-difference step size in Angstroms.
double step = 1.0e-5;
if (nargs > 1) {
    step = Double.parseDouble(args[1]);
}

// Print out the energy for each step.
boolean print = false;
if (nargs > 2) {
    print = Boolean.parseBoolean(args[2]);
}

// Things below this line normally do not need to be changed.
// ===============================================================================================

println("\n Testing the atomic coordinate gradient of " + filename);

open(filename);

ForceFieldEnergy energy = active.getPotentialEnergy();
Atom[] atoms = active.getAtomArray();
int n = atoms.length;

double gradientTolerance = 1.0e-3;
double width = 2.0 * step;
double[] x = new double[n*3];
double[] analytic = new double[3*n];
double[] numeric = new double[3];

energy.getCoordinates(x);
energy.energyAndGradient(x,analytic);

for (int i=0; i<n; i++) {
    Atom a0 = atoms[i];
    int i3 = i*3;
    int i0 = i3 + 0;
    int i1 = i3 + 1;
    int i2 = i3 + 2;

    // Find numeric dX
    double orig = x[i0];
    x[i0] += step;
    energy.setCoordinates(x);
    double e = energy.energy(false, print);    
    x[i0] = orig - step;
    energy.setCoordinates(x);
    e -= energy.energy(false, print);
    x[i0] = orig;
    numeric[0] = e / width;

    // Find numeric dY
    orig = x[i1];
    x[i1] += step;
    energy.setCoordinates(x);
    e = energy.energy(false, print);
    x[i1] = orig - step;
    energy.setCoordinates(x);
    e -= energy.energy(false, print);
    x[i1] = orig;
    numeric[1] = e / width;

    // Find numeric dZ
    orig = x[i2];
    x[i2] += step;
    energy.setCoordinates(x);
    e = energy.energy(false, print);
    x[i2] = orig - step;
    energy.setCoordinates(x);
    e -= energy.energy(false, print);
    x[i2] = orig;
    numeric[2] = e / width;

    double dx = analytic[i0] - numeric[0];
    double dy = analytic[i1] - numeric[1];
    double dz = analytic[i2] - numeric[2];
    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (len > gradientTolerance) {
        System.out.println(" " + a0.toShortString() + String.format(" failed: %10.6f.", len) 
            + String.format("\n Analytic: (%12.4f, %12.4f, %12.4f)\n", analytic[i0], analytic[i1], analytic[i2]) 
            + String.format(" Numeric:  (%12.4f, %12.4f, %12.4f)\n", numeric[0], numeric[1], numeric[2]));
        return;
    } else {
        System.out.println(" " + a0.toShortString() + String.format(" passed: %10.6f.", len) 
            + String.format("\n Analytic: (%12.4f, %12.4f, %12.4f)\n", analytic[i0], analytic[i1], analytic[i2]) 
            + String.format(" Numeric:  (%12.4f, %12.4f, %12.4f)\n", numeric[0], numeric[1], numeric[2]));
    }
}