/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package se.sics.gvod.simulation;

import se.sics.kompics.Kompics;
import se.sics.kompics.Scheduler;
import se.sics.kompics.p2p.experiment.dsl.SimulationScenario;
import se.sics.kompics.simulation.SimulatorScheduler;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */

public class Main {
    public static SimulatorScheduler scheduler;
    public static SimulationScenario scenario;
    public static long seed;
    
    public static void main(String[] args) {
        long seed = 12;
        start();
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            System.exit(1);
        }
    }

    public static void stop() {
        Kompics.shutdown();
    }
    
    public static void start() {
        scheduler = new SimulatorScheduler();
        scenario = ScenarioGen.simpleBoot(seed, 10);
        
        Kompics.setScheduler(scheduler);
        Kompics.createAndStart(Launcher.class, 1);
    }
}
