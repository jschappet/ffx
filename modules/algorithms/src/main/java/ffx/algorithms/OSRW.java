/**
 * Title: Force Field X Description: Force Field X - Software for Molecular
 * Biophysics. Copyright: Copyright (c) Michael J. Schnieders 2001-2011
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
package ffx.algorithms;

import java.io.*;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.*;
import static java.util.Arrays.fill;

import org.apache.commons.configuration.CompositeConfiguration;

import edu.rit.mp.DoubleBuf;
import edu.rit.pj.Comm;
import edu.rit.pj.cluster.JobBackend;

import ffx.numerics.Potential;
import ffx.potential.LambdaInterface;

/**
 * An implementation of the Orthogonal Space Random Walk algorithm.
 *
 * @author Michael J. Schnieders, Wei Yang and Pengyu Ren
 */
public class OSRW implements Potential {

    private static final Logger logger = Logger.getLogger(OSRW.class.getName());
    /**
     * A potential energy that implements the LambdaInterface.
     */
    private LambdaInterface lambdaInterface;
    /**
     * The potential energy of the system.
     */
    private Potential potential;
    /**
     * Number of variables.
     */
    private int nVariables;
    /**
     * Each walker has a unique lambda restart file.
     */
    private File lambdaFile;
    /**
     * Each walker reads the same histogram restart file. Only the walker of
     * rank 0 writes the histogram restart file.
     */
    private File histogramFile;
    /**
     * State variable lambda ranges from 0.0 .. 1.0.
     */
    private double lambda;
    /**
     * Flag to indicate that the Lambda particle should be propogated.
     */
    private boolean propagateLambda = true;
    /**
     * Number of times the OSRW biasing potential has been evaluated with the
     * "propagateLambda" flag true.
     */
    private int energyCount;
    /**
     * The first Lambda bin is centered on 0.0 (-0.005 .. 0.005). The final
     * Lambda bin is centered on 1.0 ( 0.995 .. 1.005).
     *
     * With this scheme, the maximum of biasing Gaussians is at the edges.
     */
    private int lambdaBins = 201;
    /**
     * It is useful to have an odd number of bins, so that there is a bin from
     * FL=-dFL/2 to dFL/2 so that as FL approaches zero its contribution to
     * thermodynamic integration goes to zero. Otherwise a contribution of zero
     * from a L bin can only result from equal sampling of the ranges -dFL to 0
     * and 0 to dFL.
     */
    private int FLambdaBins = 401;
    /**
     * The recursion kernel stores the number of visits to each
     * [lambda][Flambda] bin.
     */
    private int recursionKernel[][];
    /**
     * The recursionCount stores the [Lambda, FLambda] count for each process.
     * Therefore the array is of size [number of Processes][2]. Each 2 entry
     * array must be wrapped inside a Parallel Java IntegerBuf for the
     * All-Gather communication calls.
     */
    private double recursionCounts[][];
    private final double myRecursionCount[];
    /**
     * These DoubleBufs wrap the recusionCount arrays.
     */
    private final DoubleBuf recursionCountsBuf[];
    private final DoubleBuf myRecursionCountBuf;
    /**
     * Parallel Java world communicator.
     */
    private final Comm world;
    /**
     * Number of processes.
     */
    private final int numProc;
    /**
     * Rank of this process.
     */
    private final int rank;
    /**
     * A reference to the JobBackend to log messages for the Webserver.
     */
    private final JobBackend jobBackend;
    /**
     * When evaluating the biasing potential, contributions from Gaussians
     * centered more the "biasCutoff" away will be neglected.
     */
    private int biasCutoff = 5;
    /**
     * Width of the lambda bin.
     */
    private double dL = 1.0 / (lambdaBins - 1);
    /**
     * Half the width of a lambda bin.
     */
    private double dL_2 = dL / 2.0;
    /**
     * The width of the F_lambda bin.
     */
    private double dFL = 2.0;
    /**
     * Half the width of the F_lambda bin.
     */
    private double dFL_2 = dFL / 2.0;
    /**
     * The minimum value of the first lambda bin.
     */
    private double minLambda = -dL_2;
    /**
     * The minimum value of the first F_lambda bin.
     */
    private double minFLambda = -(dFL * FLambdaBins) / 2.0;
    /**
     * The maximum value of the last F_lambda bin.
     */
    private double maxFLambda = minFLambda + FLambdaBins * dFL;
    /**
     * Total partial derivative of the potential being sampled w.r.t. lambda.
     */
    private double dEdLambda;
    /**
     * 2nd partial derivative of the potential being sampled w.r.t lambda.
     */
    private double d2EdLambda2;
    private double dUdXdL[] = null;
    private double biasMag = 0.001;
    private double FLambda[];
    /**
     * Gas constant (in Kcal/mole/Kelvin).
     */
    public static final double R = 1.9872066e-3;
    private double theta;
    /**
     * Reasonable thetaFriction is 60 1/ps.
     */
    private double thetaFriction = 1.0e-19;
    /**
     * Reasonable thetaMass is 100 a.m.u.
     */
    private double thetaMass = 1.0e-18;
    private double halfThetaVelocity = 0.0;
    private Random stochasticRandom;
    /**
     * Random force conversion to kcal/mol/A;
     */
    private static final double randomConvert = sqrt(4.184) / 10e9;
    /**
     * randomConvert squared.
     */
    private static final double randomConvert2 = randomConvert * randomConvert;
    /**
     * Time step in picoseconds.
     */
    private double dt;
    /**
     * Temperature in Kelvin.
     */
    private double temperature;
    /**
     * Interval between adding a count to the Recursion kernel in steps.
     */
    private int countInterval = 10;
    /**
     * Interval between printing information on the lambda particle in steps.
     */
    private int printFrequency = 100;
    /**
     * Interval between how often the free energy is updated from the count
     * matrix.
     */
    private int fLambdaPrintInterval = 10;
    private int fLambdaUpdates = 0;
    /**
     * Interval between writing an OSRW restart file in steps.
     */
    private int saveFrequency = 1000;
    /**
     * Print detailed energy information.
     */
    private boolean print = false;
    /**
     * total system energy
     */
    private double totalEnergy;
    /**
     * Are FAST varying energy terms being computed, SLOW varying energy terms,
     * or BOTH. OSRW is not active when only FAST varying energy terms are being
     * propogated.
     */
    private STATE state = STATE.BOTH;
    /**
     * Flag to indicate if OSRW should send and receive counts between processes
     * synchronously or asynchronously. The latter is faster by ~40% because
     * simulation with Lambda > 0.75 must compute two condensed phase
     * self-consistent fields to interpolate polarization.
     */
    private final boolean asynchronous;
    /**
     * The ReceiveThread accumulates OSRW statistics from multiple asynchronous
     * walkers.
     */
    private ReceiveThread receiveThread;

    /**
     * OSRW Asynchronous MultiWalker Constructor.
     *
     * @param lambdaInterface
     * @param potential
     * @param lambdaFile
     * @param histogramFile
     * @param properties
     * @param temperature
     * @param dt
     * @param printInterval
     * @param saveInterval
     * @param asynchronous
     */
    public OSRW(LambdaInterface lambdaInterface, Potential potential,
            File lambdaFile, File histogramFile, CompositeConfiguration properties,
            double temperature, double dt, double printInterval,
            double saveInterval, boolean asynchronous) {
        this.lambdaInterface = lambdaInterface;
        this.potential = potential;
        this.lambdaFile = lambdaFile;
        this.histogramFile = histogramFile;
        this.temperature = temperature;
        this.asynchronous = asynchronous;
        nVariables = potential.getNumberOfVariables();

        /**
         * Convert the time step to picoseconds.
         */
        this.dt = dt * 0.001;

        /**
         * Convert the print interval to a print frequency.
         */
        printFrequency = 100;
        if (printInterval >= this.dt) {
            printFrequency = (int) (printInterval / this.dt);
        }

        /**
         * Convert the save interval to a save frequency.
         */
        saveFrequency = 1000;
        if (saveInterval >= this.dt) {
            saveFrequency = (int) (saveInterval / this.dt);
        }

        biasCutoff = properties.getInt("lambda-bias-cutoff", 5);
        biasMag = properties.getDouble("bias-gaussian-mag", 0.005);
        dL = properties.getDouble("lambda-bin-width", 0.005);
        dFL = properties.getDouble("flambda-bin-width", 2.0);

        /**
         * Require modest sampling of the lambda path.
         */
        if (dL > 0.1) {
            dL = 0.1;
        }

        /**
         * Many lambda bin widths do not evenly divide into 1.0; here we correct
         * for this by computing an integer number of bins, then re-setting the
         * lambda variable appropriately. Note that we also choose to have an
         * odd number of lambda bins, so that the centers of the first and last
         * bin are at 0 and 1.
         */
        lambdaBins = (int) (1.0 / dL);
        if (lambdaBins % 2 == 0) {
            lambdaBins++;
        }

        /**
         * The initial number of FLambda bins does not really matter, since a
         * larger number is automatically allocated as needed. The center of the
         * central bin is at 0.
         */
        FLambdaBins = 401;
        minFLambda = -(dFL * FLambdaBins) / 2.0;

        /**
         * Allocate space for the recursion kernel that stores counts.
         */
        recursionKernel = new int[lambdaBins][FLambdaBins];

        /**
         * Load the OSRW histogram restart file if it exists.
         */
        boolean readHistogramRestart = false;
        if (histogramFile != null && histogramFile.exists()) {
            try {
                OSRWHistogramReader osrwHistogramReader = new OSRWHistogramReader(new FileReader(histogramFile));
                osrwHistogramReader.readHistogramFile();
                logger.info(String.format("\n Continuing OSRW histogram from %s.", histogramFile.getName()));
                readHistogramRestart = true;
            } catch (FileNotFoundException ex) {
                logger.info(" Histogram restart file could not be found and will be ignored.");
            }
        }

        /**
         * Load the OSRW lambda restart file if it exists.
         */
        if (lambdaFile != null && lambdaFile.exists()) {
            try {
                OSRWLambdaReader osrwLambdaReader = new OSRWLambdaReader(new FileReader(lambdaFile));
                osrwLambdaReader.readLambdaFile();
                logger.info(String.format("\n Continuing OSRW lambda from %s.", lambdaFile.getName()));
            } catch (FileNotFoundException ex) {
                logger.info(" Lambda restart file could not be found and will be ignored.");
            }
        }

        dL = 1.0 / (lambdaBins - 1);
        dL_2 = dL / 2.0;
        minLambda = -dL_2;
        dFL_2 = dFL / 2.0;
        maxFLambda = minFLambda + FLambdaBins * dFL;
        FLambda = new double[lambdaBins];
        dUdXdL = new double[nVariables];
        energyCount = -1;
        stochasticRandom = new Random(0);

        /**
         * Set up the multi-walker communication variables for Parallel Java
         * communication between nodes.
         */
        world = Comm.world();
        numProc = world.size();
        rank = world.rank();
        jobBackend = JobBackend.getJobBackend();

        if (asynchronous) {
            /**
             * Use asynchronous communication.
             */
            myRecursionCount = new double[2];
            myRecursionCountBuf = DoubleBuf.buffer(myRecursionCount);
            receiveThread = new ReceiveThread();
            receiveThread.start();
            recursionCounts = null;
            recursionCountsBuf = null;
        } else {
            /**
             * Use synchronous communication.
             */
            recursionCounts = new double[numProc][2];
            recursionCountsBuf = new DoubleBuf[numProc];
            for (int i = 0; i < numProc; i++) {
                recursionCountsBuf[i] = DoubleBuf.buffer(recursionCounts[i]);
            }
            myRecursionCount = recursionCounts[rank];
            myRecursionCountBuf = recursionCountsBuf[rank];
            receiveThread = null;
        }

        /**
         * Log OSRW parameters.
         */
        logger.info(" Orthogonal Space Random Walk Parameters");
        logger.info(String.format(" Gaussian Bias Magnitude:        %6.5f (kcal/mole)", biasMag));
        logger.info(String.format(" Gaussian Bias Cutoff:           %6d bins", biasCutoff));

        /**
         * Update and print out the recursion slave.
         */
        if (readHistogramRestart) {
            updateFLambda(true);
        }

    }

    public void setPropagateLambda(boolean propagateLambda) {
        this.propagateLambda = propagateLambda;
    }

    private int binForLambda(double lambda) {
        int lambdaBin = (int) floor((lambda - minLambda) / dL);
        if (lambdaBin < 0) {
            lambdaBin = 0;
        }
        if (lambdaBin >= lambdaBins) {
            lambdaBin = lambdaBins - 1;
        }
        return lambdaBin;
    }

    private int binForFLambda(double dEdLambda) {
        int FLambdaBin = (int) floor((dEdLambda - minFLambda) / dFL);
        if (FLambdaBin == FLambdaBins) {
            FLambdaBin = FLambdaBins - 1;
        }
        assert (FLambdaBin < FLambdaBins);
        assert (FLambdaBin >= 0);
        return FLambdaBin;
    }

    @Override
    public double energyAndGradient(double[] x, double[] gradient) {

        /**
         * OSRW is propogated with the slowly varying terms.
         */
        if (state == STATE.FAST) {
            return potential.energyAndGradient(x, gradient);
        }

        if (propagateLambda) {
            energyCount++;
        }

        if (propagateLambda && energyCount > 0) {
            /**
             * Metadynamics grid counts (every 'countInterval' steps).
             */
            if (energyCount % countInterval == 0) {
                System.setProperty("use-precise-polar-eps", "true");
            } else {
                System.setProperty("use-precise-polar-eps", "false");
            }
        }

        double e = potential.energyAndGradient(x, gradient);

        double biasEnergy = 0.0;
        dEdLambda = lambdaInterface.getdEdL();
        d2EdLambda2 = lambdaInterface.getd2EdL2();

        int lambdaBin = binForLambda(lambda);
        int FLambdaBin = binForFLambda(dEdLambda);

        /**
         * Calculate recursion kernel G(L, F_L) and its derivatives with respect
         * to L and F_L.
         */
        double dGdLambda = 0.0;
        double dGdFLambda = 0.0;
        double ls2 = (2.0 * dL) * (2.0 * dL);
        double FLs2 = (2.0 * dFL) * (2.0 * dFL);
        for (int iL = -biasCutoff; iL <= biasCutoff; iL++) {
            int lcenter = lambdaBin + iL;
            double deltaL = lambda - (lcenter * dL);
            double deltaL2 = deltaL * deltaL;
            // Mirror conditions for recursion kernel counts.
            int lcount = lcenter;
            double mirrorFactor = 1.0;
            if (lcount == 0 || lcount == lambdaBins - 1) {
                mirrorFactor = 2.0;
            } else if (lcount < 0) {
                lcount = -lcount;
            } else if (lcount > lambdaBins - 1) {
                // Number of bins past the last bin
                lcount -= (lambdaBins - 1);
                // Mirror bin
                lcount = lambdaBins - 1 - lcount;
            }
            for (int iFL = -biasCutoff; iFL <= biasCutoff; iFL++) {
                int FLcenter = FLambdaBin + iFL;
                /**
                 * If either of the following FL edge conditions are true, then
                 * there are no counts and we continue.
                 */
                if (FLcenter < 0 || FLcenter >= FLambdaBins) {
                    continue;
                }
                double deltaFL = dEdLambda - (minFLambda + FLcenter * dFL + dFL_2);
                double deltaFL2 = deltaFL * deltaFL;
                double weight = mirrorFactor * recursionKernel[lcount][FLcenter];
                double bias = weight * biasMag
                        * exp(-deltaL2 / (2.0 * ls2))
                        * exp(-deltaFL2 / (2.0 * FLs2));
                biasEnergy += bias;
                dGdLambda -= deltaL / ls2 * bias;
                dGdFLambda -= deltaFL / FLs2 * bias;
            }
        }

        /**
         * Lambda gradient due to recursion kernel G(L, F_L).
         */
        double dEdU = dEdLambda;
        dEdLambda += dGdLambda + dGdFLambda * d2EdLambda2;

        /**
         * Cartesian coordinate gradient due to recursion kernel G(L, F_L).
         */
        fill(dUdXdL, 0.0);
        lambdaInterface.getdEdXdL(dUdXdL);
        for (int i = 0; i < nVariables; i++) {
            gradient[i] += dGdFLambda * dUdXdL[i];
        }


        if (propagateLambda && energyCount > 0) {
            /**
             * Update free energy F(L) every ~10 steps.
             */
            if (energyCount % 10 == 0) {
                fLambdaUpdates++;
                boolean printFLambda = fLambdaUpdates % fLambdaPrintInterval == 0;
                updateFLambda(printFLambda);
            }
            if (energyCount % saveFrequency == 0) {
                /**
                 * Only the rank 0 process writes the histogram restart file.
                 */
                if (rank == 0) {
                    try {
                        OSRWHistogramWriter osrwHistogramRestart = new OSRWHistogramWriter(new BufferedWriter(new FileWriter(histogramFile)));
                        osrwHistogramRestart.writeHistogramFile();
                        osrwHistogramRestart.flush();
                        osrwHistogramRestart.close();
                        logger.info(String.format(" Wrote OSRW histogram restart file to %s.", histogramFile.getName()));
                    } catch (IOException ex) {
                        String message = " Exception writing OSRW histogram restart file.";
                        logger.log(Level.INFO, message, ex);
                    }
                }
                /**
                 * All ranks write a lambda restart file.
                 */
                try {
                    OSRWLambdaWriter osrwLambdaRestart = new OSRWLambdaWriter(new BufferedWriter(new FileWriter(lambdaFile)));
                    osrwLambdaRestart.writeLambdaFile();
                    osrwLambdaRestart.flush();
                    osrwLambdaRestart.close();
                    logger.info(String.format(" Wrote OSRW lambda restart file to %s.", lambdaFile.getName()));
                } catch (IOException ex) {
                    String message = " Exception writing OSRW lambda restart file.";
                    logger.log(Level.INFO, message, ex);
                }
            }
        }

        /**
         * Compute the energy and gradient for the recursion slave at F(L) using
         * interpolation.
         */
        biasEnergy += computeFreeEnergy();

        if (print) {
            logger.info(String.format(" %s %16.8f", "Bias Energy       ", biasEnergy));
            logger.info(String.format(" %s %16.8f  %s",
                    "OSRW Potential    ", e + biasEnergy, "(Kcal/mole)"));
        }

        if (propagateLambda && energyCount > 0) {
            /**
             * Log the current Lambda state.
             */
            if (energyCount % printFrequency == 0) {
                if (lambdaBins < 1000) {
                    logger.info(String.format(" L=%6.4f (%3d) F_LU=%10.4f F_LB=%10.4f F_L=%10.4f",
                            lambda, lambdaBin, dEdU, dEdLambda - dEdU, dEdLambda));
                } else {
                    logger.info(String.format(" L=%6.4f (%4d) F_LU=%10.4f F_LB=%10.4f F_L=%10.4f",
                            lambda, lambdaBin, dEdU, dEdLambda - dEdU, dEdLambda));
                }
            }
            /**
             * Metadynamics grid counts (every 'countInterval' steps).
             */
            if (energyCount % countInterval == 0) {
                if (jobBackend != null) {
                    jobBackend.setComment(String.format("[L=%6.4f, F_L=%10.4f] at %7.3e psec", lambda, dEdU, energyCount * dt));
                }
                if (asynchronous) {
                    asynchronousSend(lambda, dEdU);
                } else {
                    synchronousSend(lambda, dEdU);
                }
            }
        }
        /**
         * Propagate the Lambda particle.
         */
        if (propagateLambda) {
            langevin();
        }

        totalEnergy = e + biasEnergy;

        return totalEnergy;
    }

    /**
     * Send an OSRW count to all other processes while also receiving an OSRW
     * count from all other processes.
     *
     * @param lambda
     * @param dEdU
     */
    private void synchronousSend(double lambda, double dEdU) {
        /**
         * All-Gather counts from each walker.
         */
        myRecursionCount[0] = lambda;
        myRecursionCount[1] = dEdU;
        try {
            world.allGather(myRecursionCountBuf, recursionCountsBuf);
        } catch (Exception ex) {
            String message = " Multi-walker OSRW allGather failed.";
            logger.log(Level.SEVERE, message, ex);
        }

        /**
         * Find the minimum and maximum FLambda bin for the gathered counts.
         */
        double minRequired = Double.MAX_VALUE;
        double maxRequired = Double.MIN_VALUE;
        for (int i = 0; i < numProc; i++) {
            minRequired = Math.min(minRequired, recursionCounts[i][1]);
            maxRequired = Math.max(maxRequired, recursionCounts[i][1]);
        }

        /**
         * Check that the FLambda range of the Recursion kernel includes both
         * the minimum and maximum FLambda value.
         */
        checkRecursionKernelSize(minRequired);
        checkRecursionKernelSize(maxRequired);

        /**
         * Increment the Recursion Kernel based on the input of each walker.
         */
        for (int i = 0; i < numProc; i++) {
            int walkerLambda = binForLambda(recursionCounts[i][0]);
            int walkerFLambda = binForFLambda(recursionCounts[i][1]);
            recursionKernel[walkerLambda][walkerFLambda]++;
        }
    }

    /**
     * Send an OSRW count to all other processes.
     *
     * @param lambda
     * @param dEdU
     */
    private void asynchronousSend(double lambda, double dEdU) {
        myRecursionCount[0] = lambda;
        myRecursionCount[1] = dEdU;
        for (int i = 0; i < numProc; i++) {
            try {
                world.send(i, myRecursionCountBuf);
            } catch (Exception ex) {
                String message = " Asynchronous Multiwalker OSRW send failed.";
                logger.log(Level.SEVERE, message, ex);
            }
        }
    }

    public double getTotaldEdLambda() {
        return dEdLambda;
    }

    /**
     * If necessary, allocate more space.
     */
    private void checkRecursionKernelSize(double dEdLambda) {
        if (dEdLambda > maxFLambda) {
            logger.info(String.format(" Current F_lambda %8.2f > maximum historgram size %8.2f.",
                    dEdLambda, maxFLambda));

            double origDeltaG = updateFLambda(false);

            int newFLambdaBins = FLambdaBins;
            while (minFLambda + newFLambdaBins * dFL < dEdLambda) {
                newFLambdaBins += 100;
            }
            int newRecursionKernel[][] = new int[lambdaBins][newFLambdaBins];
            /**
             * We have added bins above the indeces of the current counts just
             * copy them into the new array.
             */
            for (int i = 0; i < lambdaBins; i++) {
                for (int j = 0; j < FLambdaBins; j++) {
                    newRecursionKernel[i][j] = recursionKernel[i][j];
                }
            }
            recursionKernel = newRecursionKernel;
            FLambdaBins = newFLambdaBins;
            maxFLambda = minFLambda + dFL * FLambdaBins;
            logger.info(String.format(" New historgram %8.2f to %8.2f with %d bins.\n",
                    minFLambda, maxFLambda, FLambdaBins));

            assert (origDeltaG == updateFLambda(false));

        }
        if (dEdLambda < minFLambda) {
            logger.info(String.format(" Current F_lambda %8.2f < minimum historgram size %8.2f.",
                    dEdLambda, minFLambda));

            double origDeltaG = updateFLambda(false);

            int offset = 100;
            while (dEdLambda < minFLambda - offset * dFL) {
                offset += 100;
            }
            int newFLambdaBins = FLambdaBins + offset;
            int newRecursionKernel[][] = new int[lambdaBins][newFLambdaBins];
            /**
             * We have added bins below the current counts, so their indeces
             * must be increased by: offset = newFLBins - FLBins
             */
            for (int i = 0; i < lambdaBins; i++) {
                for (int j = 0; j < FLambdaBins; j++) {
                    newRecursionKernel[i][j + offset] = recursionKernel[i][j];
                }
            }
            recursionKernel = newRecursionKernel;
            minFLambda = minFLambda - offset * dFL;
            FLambdaBins = newFLambdaBins;
            logger.info(String.format(" New historgram %8.2f to %8.2f with %d bins.\n",
                    minFLambda, maxFLambda, FLambdaBins));

            assert (origDeltaG == updateFLambda(false));
        }
    }

    private double updateFLambda(boolean print) {
        double freeEnergy = 0.0;
        int totalCounts = 0;
        if (print) {
            logger.info(" Count   Lambda Bins    F_Lambda Bins   <   F_L  >       dG        G");
        }
        for (int iL = 0; iL < lambdaBins; iL++) {
            int ulFL = -1;
            int llFL = -1;

            // Find the smallest FL bin.
            for (int jFL = 0; jFL < FLambdaBins; jFL++) {
                int count = recursionKernel[iL][jFL];
                if (count > 0) {
                    llFL = jFL;
                    break;
                }
            }

            // Find the largest FL bin.
            for (int jFL = FLambdaBins - 1; jFL >= 0; jFL--) {
                int count = recursionKernel[iL][jFL];
                if (count > 0) {
                    ulFL = jFL;
                    break;
                }
            }

            int lambdaCount = 0;
            // The FL range sampled for lambda bin [iL*dL .. (iL+1)*dL]
            double lla = 0.0;
            double ula = 0.0;
            if (ulFL == -1) {
                FLambda[iL] = 0.0;
            } else {
                double ensembleAverageFLambda = 0.0;
                double partitionFunction = 0.0;
                for (int jFL = llFL; jFL <= ulFL; jFL++) {
                    double currentFLambda = minFLambda + jFL * dFL + dFL_2;
                    double weight = exp(evaluateKernel(iL, jFL) / (R * temperature));
                    ensembleAverageFLambda += currentFLambda * weight;
                    partitionFunction += weight;
                    lambdaCount += recursionKernel[iL][jFL];
                }
                FLambda[iL] = ensembleAverageFLambda / partitionFunction;
                lla = minFLambda + llFL * dFL;
                ula = minFLambda + (ulFL + 1) * dFL;
            }

            // The first and last lambda bins are half size.
            double delta = dL;
            if (iL == 0 || iL == lambdaBins - 1) {
                delta = dL_2;
            }
            double deltaFreeEnergy = FLambda[iL] * delta;
            freeEnergy += deltaFreeEnergy;
            totalCounts += lambdaCount;

            if (print) {
                double llL = iL * dL - dL_2;
                double ulL = llL + dL;
                if (llL < 0.0) {
                    llL = 0.0;
                }
                if (ulL > 1.0) {
                    ulL = 1.0;
                }
                logger.info(String.format(" %6d  %5.3f %5.3f   %7.1f %7.1f   %8.3f  %8.3f %8.3f",
                        lambdaCount, llL, ulL, lla, ula,
                        FLambda[iL], deltaFreeEnergy, freeEnergy));
            }
        }
        logger.info(String.format(" The free energy is %12.4f kcal/mol from %d counts.", freeEnergy, totalCounts));

        return freeEnergy;
    }

    private double computeFreeEnergy() {
        double biasEnergy = 0.0;
        for (int iL0 = 0; iL0 < lambdaBins - 1; iL0++) {
            int iL1 = iL0 + 1;
            /**
             * Find bin centers and values for interpolation / extrapolation
             * points.
             */
            double L0 = iL0 * dL;
            double L1 = L0 + dL;
            double FL0 = FLambda[iL0];
            double FL1 = FLambda[iL1];
            double deltaFL = FL1 - FL0;
            /**
             * If the lambda is less than or equal to the upper limit, this is
             * the final interval. Set the upper limit to L, compute the partial
             * derivative and break.
             */
            boolean done = false;
            if (lambda <= L1) {
                done = true;
                L1 = lambda;
            }
            /**
             * Upper limit - lower limit of the integral of the extrapolation /
             * interpolation.
             */
            biasEnergy += (FL0 * L1 + deltaFL * L1 * (0.5 * L1 - L0) / dL);
            biasEnergy -= (FL0 * L0 + deltaFL * L0 * (-0.5 * L0) / dL);
            if (done) {
                /**
                 * Compute the gradient d F(L) / dL at L.
                 */
                dEdLambda -= FL0 + (L1 - L0) * deltaFL / dL;
                break;
            }
        }
        return -biasEnergy;
    }

    private double evaluateKernel(int cLambda, int cF_Lambda) {
        /**
         * Compute the value of L and FL for the center of the current bin.
         */
        double vL = cLambda * dL;
        double vFL = minFLambda + cF_Lambda * dFL + dFL_2;
        /**
         * Set the variances for the Gaussian bias.
         */
        double Ls2 = 2.0 * dL * 2.0 * dL;
        double FLs2 = 2.0 * dFL * 2.0 * dFL;
        double sum = 0.0;
        for (int iL = -biasCutoff; iL <= biasCutoff; iL++) {
            int Lcenter = cLambda + iL;
            double deltaL = vL - Lcenter * dL;
            double deltaL2 = deltaL * deltaL;

            // Mirror condition for Lambda counts.
            int lcount = Lcenter;
            double mirrorFactor = 1.0;
            if (lcount == 0 || lcount == lambdaBins - 1) {
                /**
                 * The width of the first and last bins is dLambda_2, so the
                 * mirror condition is to double their counts.
                 */
                mirrorFactor = 2.0;
            } else if (lcount < 0) {
                lcount = -lcount;
            } else if (lcount > lambdaBins - 1) {
                // number of bins past the last bin.
                lcount -= (lambdaBins - 1);
                // mirror bin
                lcount = lambdaBins - 1 - lcount;
            }

            for (int jFL = -biasCutoff; jFL <= biasCutoff; jFL++) {
                int FLcenter = cF_Lambda + jFL;
                /**
                 * For FLambda outside the count matrix the weight is 0 so we
                 * continue.
                 */
                if (FLcenter < 0 || FLcenter >= FLambdaBins) {
                    continue;
                }
                double deltaFL = vFL - (minFLambda + FLcenter * dFL + dFL_2);
                double deltaFL2 = deltaFL * deltaFL;
                double weight = mirrorFactor * recursionKernel[lcount][FLcenter];
                if (weight > 0) {
                    double e = weight * biasMag * exp(-deltaL2 / (2.0 * Ls2))
                            * exp(-deltaFL2 / (2.0 * FLs2));
                    sum += e;
                }
            }
        }
        return sum;
    }

    public void setLambda(double lambda) {
        lambdaInterface.setLambda(lambda);
        this.lambda = lambda;
        theta = Math.asin(Math.sqrt(lambda));
    }

    public void setThetaMass(double thetaMass) {
        this.thetaMass = thetaMass;
    }

    public void setThetaFrication(double thetaFriction) {
        this.thetaFriction = thetaFriction;
    }

    /**
     * Set the OSRW Gaussian biasing potential magnitude (kcal/mole).
     *
     * @param biasMag Gaussian biasing potential magnitude (kcal/mole)
     */
    public void setBiasMagnitude(double biasMag) {
        this.biasMag = biasMag;
    }

    /**
     * Set the OSRW count interval. Every 'countInterval' steps the
     * recursionKernel will be incremented based on the current value of the
     * lambda state variable and the derivative of the energy with respect to
     * lambda (dU/dL).
     *
     * @param countInterval Molecular dynamics steps between counts.
     */
    public void setCountInterval(int countInterval) {
        if (countInterval > 0) {
            this.countInterval = countInterval;
        } else {
            logger.info(" OSRW count interval must be greater than 0.");
        }
    }

    /**
     * Propagate Lambda using Langevin dynamics.
     */
    private void langevin() {
        double rt2 = 2.0 * Thermostat.R * temperature * thetaFriction / dt;
        double randomForce = sqrt(rt2) * stochasticRandom.nextGaussian() / randomConvert;
        double dEdL = -dEdLambda * sin(2.0 * theta);
        halfThetaVelocity = (halfThetaVelocity * (2.0 * thetaMass - thetaFriction * dt)
                + randomConvert2 * 2.0 * dt * (dEdL + randomForce))
                / (2.0 * thetaMass + thetaFriction * dt);
        theta = theta + dt * halfThetaVelocity;

        if (theta > PI) {
            theta -= 2.0 * PI;
        } else if (theta <= -PI) {
            theta += 2.0 * PI;
        }

        double sinTheta = sin(theta);
        lambda = sinTheta * sinTheta;
        lambdaInterface.setLambda(lambda);
    }

    @Override
    public void setScaling(double[] scaling) {
        potential.setScaling(scaling);
    }

    @Override
    public double[] getScaling() {
        return potential.getScaling();
    }

    @Override
    public double[] getCoordinates(double[] doubles) {
        return potential.getCoordinates(doubles);
    }

    @Override
    public double[] getMass() {
        return potential.getMass();
    }

    /**
     * Return a reference to each variables type.
     *
     * @return the type of each variable.
     */
    @Override
    public VARIABLE_TYPE[] getVariableTypes() {
        return potential.getVariableTypes();
    }

    @Override
    public double getTotal() {
        return totalEnergy;
    }

    @Override
    public int getNumberOfVariables() {
        return potential.getNumberOfVariables();
    }

    @Override
    public void setEnergyTermState(STATE state) {
        this.state = state;
        potential.setEnergyTermState(state);
    }

    private class OSRWHistogramWriter extends PrintWriter {

        public OSRWHistogramWriter(Writer writer) {
            super(writer);
        }

        public void writeHistogramFile() {
            printf("Temperature     %15.3f\n", temperature);
            printf("Lambda-Mass     %15.8e\n", thetaMass);
            printf("Lambda-Friction %15.8e\n", thetaFriction);
            printf("Bias-Mag        %15.8e\n", biasMag);
            printf("Bias-Cutoff     %15d\n", biasCutoff);
            printf("Count-Interval  %15d\n", countInterval);
            printf("Lambda-Bins     %15d\n", lambdaBins);
            printf("FLambda-Bins    %15d\n", FLambdaBins);
            printf("Flambda-Min     %15.8e\n", minFLambda);
            printf("Flambda-Width   %15.8e\n", dFL);
            for (int i = 0; i < lambdaBins; i++) {
                printf("%d", recursionKernel[i][0]);
                for (int j = 1; j < FLambdaBins; j++) {
                    printf(" %d", recursionKernel[i][j]);
                }
                println();
            }
        }
    }

    private class OSRWLambdaWriter extends PrintWriter {

        public OSRWLambdaWriter(Writer writer) {
            super(writer);
        }

        public void writeLambdaFile() {
            printf("Lambda          %15.8f\n", lambda);
            printf("Lambda-Velocity %15.8e\n", halfThetaVelocity);
        }
    }

    private class OSRWHistogramReader extends BufferedReader {

        public OSRWHistogramReader(Reader reader) {
            super(reader);
        }

        public void readHistogramFile() {
            try {
                temperature = Double.parseDouble(readLine().split(" +")[1]);
                thetaMass = Double.parseDouble(readLine().split(" +")[1]);
                thetaFriction = Double.parseDouble(readLine().split(" +")[1]);
                biasMag = Double.parseDouble(readLine().split(" +")[1]);
                biasCutoff = Integer.parseInt(readLine().split(" +")[1]);
                countInterval = Integer.parseInt(readLine().split(" +")[1]);
                lambdaBins = Integer.parseInt(readLine().split(" +")[1]);
                FLambdaBins = Integer.parseInt(readLine().split(" +")[1]);
                minFLambda = Double.parseDouble(readLine().split(" +")[1]);
                dFL = Double.parseDouble(readLine().split(" +")[1]);
                // Allocate memory for the recursion kernel.
                recursionKernel = new int[lambdaBins][FLambdaBins];
                for (int i = 0; i < lambdaBins; i++) {
                    String counts[] = readLine().split(" +");
                    for (int j = 0; j < FLambdaBins; j++) {
                        recursionKernel[i][j] = Integer.parseInt(counts[j]);
                    }
                }
            } catch (Exception e) {
                String message = " Invalid OSRW Histogram file.";
                logger.log(Level.SEVERE, message, e);
            }
        }
    }

    private class OSRWLambdaReader extends BufferedReader {

        public OSRWLambdaReader(Reader reader) {
            super(reader);
        }

        public void readLambdaFile() {
            try {
                lambda = Double.parseDouble(readLine().split(" +")[1]);
                halfThetaVelocity = Double.parseDouble(readLine().split(" +")[1]);
                setLambda(lambda);
            } catch (Exception e) {
                String message = " Invalid OSRW Lambda file.";
                logger.log(Level.SEVERE, message, e);
            }
        }
    }

    class ReceiveThread extends Thread {

        final double recursionCount[];
        final DoubleBuf recursionCountBuf;

        public ReceiveThread() {
            recursionCount = new double[2];
            recursionCountBuf = DoubleBuf.buffer(recursionCount);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    world.receive(null, recursionCountBuf);
                } catch (Exception e) {
                    String message = e.getMessage();
                    logger.log(Level.WARNING, message, e);
                }
                /**
                 * Check that the FLambda range of the Recursion kernel includes
                 * both the minimum and maximum FLambda value.
                 */
                checkRecursionKernelSize(recursionCount[1]);

                /**
                 * Increment the Recursion Kernel based on the input of current
                 * walker.
                 */
                int walkerLambda = binForLambda(recursionCount[0]);
                int walkerFLambda = binForFLambda(recursionCount[1]);
                recursionKernel[walkerLambda][walkerFLambda]++;
            }
        }
    }
}
