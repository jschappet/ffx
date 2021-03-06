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
package ffx.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;

import javax.swing.Timer;

import ffx.potential.bonded.Atom;
import ffx.ui.commands.FFXClient;
import ffx.ui.commands.SimulationFilter;
import ffx.ui.commands.TinkerSystem;
import ffx.ui.commands.TinkerUpdate;
import ffx.utilities.Keyword;

/**
 * This TinkerSimulation class oversees loading information from an executing
 * TINKER program into Force Field X.
 *
 * @author Michael J. Schnieders
 *
 */
public class TinkerSimulation implements ActionListener {
    // The client monitors a socket based connection to an executing TINKER
    // program.

    private FFXClient client;
    private SimulationFilter simulationFilter;
    private InetSocketAddress address;
    // If the TINKER program was launched from the GUI,
    // the job Thread will be alive until the program exits.
    private Thread job = null;
    // Once the simulation is finished, this flag will be true.
    private boolean finished = false;
    // The reader thread contains a SimulationFilter instance that will read a
    // simulation into the FFX.
    private Thread reader;
    private MainPanel mainPanel;
    private FFXSystem system;
    private TinkerUpdate tinkerUpdate = null;
    private boolean firstUpdate = true;
    private Timer timer;
    private int delay = 10;
    private double time = 0.0;
    private int step = 0;

    // Constructor
    /**
     * <p>
     * Constructor for TinkerSimulation.</p>
     *
     * @param s a {@link ffx.ui.FFXSystem} object.
     * @param j a {@link java.lang.Thread} object.
     * @param f a {@link ffx.ui.MainPanel} object.
     * @param a a {@link java.net.InetSocketAddress} object.
     */
    public TinkerSimulation(FFXSystem s, Thread j, MainPanel f,
            InetSocketAddress a) {
        system = s;
        job = j;
        mainPanel = f;
        address = a;
        if (address == null) {
            finished = true;
            return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        // Check if we're connected to a TINKER Server.
        if (!connect()) {
            return;
        }
        // Check if we need to initialize the Simulation System.
        if (system == null) {
            TinkerSystem sys = client.getSystem();
            if (sys != null) {
                if (simulationFilter == null) {
                    if (system == null) {
                        system = new FFXSystem(new File("Simulation"),
                                "Simulation", Keyword.loadProperties(null));
                    }
                    simulationFilter = new SimulationFilter(sys, system);
                    UIFileOpener openFile = new UIFileOpener(simulationFilter,
                            mainPanel);
                    reader = new Thread(openFile);
                    reader.start();
                } else if (simulationFilter.fileRead()) {
                    system = (FFXSystem) simulationFilter.getActiveMolecularSystem();
                    simulationFilter = null;
                }
            }
        } // A Simulation System exists, attempt to Update it.
        else {
            if (tinkerUpdate == null || tinkerUpdate.read) {
                tinkerUpdate = client.getUpdate();
            }
            if (tinkerUpdate != null && !tinkerUpdate.read
                    && !mainPanel.getGraphics3D().isSceneRendering()) {
                update();
            }
        }
    }

    /**
     * <p>
     * connect</p>
     *
     * @return a boolean.
     */
    public boolean connect() {
        if (isFinished()) {
            return false;
        }
        if (isConnected()) {
            return true;
        }
        // Create a timer to regularly wake up this TinkerSimulation.
        if (timer == null) {
            timer = new Timer(delay, this);
            timer.setCoalesce(true);
            timer.setDelay(10);
            timer.start();
        }
        // Create the FFXClient to monitor messages to/from TINKER.
        if (client == null) {
            client = new FFXClient(address);
        }
        // Try to connect.
        client.connect();
        // If connected, change to our "steady-state" timer delay.
        if (client.isConnected()) {
            timer.setDelay(delay);
            return true;
        }
        // The FFXClient and the Timer are set up, but a TINKER simulation
        // has not responded yet. This connect method will be called again
        // through "actionPerformed" when
        // the timer wakes up.
        return false;
    }

    /**
     * <p>
     * getFSystem</p>
     *
     * @return a {@link ffx.ui.FFXSystem} object.
     */
    public FFXSystem getFSystem() {
        return system;
    }

    /**
     * <p>
     * isConnected</p>
     *
     * @return a boolean.
     */
    public boolean isConnected() {
        if (client != null && client.isConnected()) {
            return true;
        }
        return false;
    }

    /**
     * <p>
     * isFinished</p>
     *
     * @return a boolean.
     */
    public boolean isFinished() {
        if (client != null && client.isClosed()) {
            finished = true;
        }
        if (job != null && !job.isAlive()) {
            finished = true;
        }
        if (finished) {
            if (timer != null) {
                timer.stop();
            }
            update();
            release();
        }
        return finished;
    }

    // Release the simulation
    /**
     * <p>
     * release</p>
     */
    public void release() {
        finished = true;
        if (timer != null) {
            timer.stop();
        }
        if (client != null) {
            client.release();
        }
        mainPanel.getMainMenu().setConnect(true);
    }

    private void update() {
        if (system.isStale()) {
            return;
        }
        if (tinkerUpdate == null || tinkerUpdate.read == true) {
            return;
        }
        // Sanity check - FFX and TINKER should agree on the number of atoms.
        List<Atom> atoms = system.getAtomList();
        int n = atoms.size();
        if (tinkerUpdate.numatoms != n) {
            finished = true;
            return;
        }
        // This is either an MD Run.
        if (tinkerUpdate.type == TinkerUpdate.SIMULATION) {
            if (tinkerUpdate.time == time) {
                tinkerUpdate.read = true;
                return;
            }
            time = tinkerUpdate.time;
        } else if (tinkerUpdate.type == TinkerUpdate.OPTIMIZATION) {
            if (tinkerUpdate.step == step) {
                tinkerUpdate.read = true;
                return;
            }
            step = tinkerUpdate.step;
        }
        // Reset the Maximum Magnitude Values, such that they will be consistent
        // with this frame of the simulation after the update.
        double d[] = new double[3];
        for (Atom a : atoms) {
            int index = a.getXYZIndex() - 1;
            d[0] = tinkerUpdate.coordinates[0][index];
            d[1] = tinkerUpdate.coordinates[1][index];
            d[2] = tinkerUpdate.coordinates[2][index];
            a.moveTo(d);
        }
        if (firstUpdate) {
            system.center();
            firstUpdate = false;
        }
        mainPanel.getGraphics3D().updateScene(system, true, false, null, false,
                null);
        mainPanel.getHierarchy().updateStatus();
        mainPanel.getHierarchy().repaint();
        tinkerUpdate.read = true;
        tinkerUpdate = client.getUpdate();
    }
}
