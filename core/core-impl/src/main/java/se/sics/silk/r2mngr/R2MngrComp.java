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
package se.sics.silk.r2mngr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ComponentProxy;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.fsm.FSMException;
import se.sics.kompics.fsm.FSMInternalState;
import se.sics.kompics.fsm.FSMStateName;
import se.sics.kompics.fsm.MultiFSM;
import se.sics.kompics.fsm.OnFSMExceptionAction;
import se.sics.kompics.fsm.id.FSMIdentifierFactory;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.network.KAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class R2MngrComp extends ComponentDefinition {

  private static final Logger LOG = LoggerFactory.getLogger(R2MngrComp.class);
  private String logPrefix;

  private Ports ports;
  private MultiFSM peerSeeders;
  private MultiFSM peerLeechers;
  private MultiFSM torrents;
  private R2ConnSeeder.ES peerSeederES;
  private R2ConnLeecher.ES peerLeecherES;
  private R2Torrent.ES torrentES;

  public R2MngrComp(Init init) {
    logPrefix = "<" + init.selfAdr.getId() + ">";
    ports = new Ports(proxy);
    subscribe(handleStart, control);
    setupFSM(init);
  }

  private void setupFSM(Init init) {
    peerSeederES = new R2ConnSeeder.ES(ports, init.selfAdr, init.retries, init.retryInterval);
    peerLeecherES = new R2ConnLeecher.ES(ports, init.selfAdr);
    torrentES = new R2Torrent.ES(ports);

    peerSeederES.setProxy(proxy);
    peerLeecherES.setProxy(proxy);
    torrentES.setProxy(proxy);
    try {
      OnFSMExceptionAction oexa = new OnFSMExceptionAction() {
        @Override
        public void handle(FSMException ex) {
          throw new RuntimeException(ex);
        }
      };
      FSMIdentifierFactory fsmIdFactory = config().getValue(FSMIdentifierFactory.CONFIG_KEY, FSMIdentifierFactory.class);
      peerSeeders = R2ConnSeeder.FSM.multifsm(fsmIdFactory, peerSeederES, oexa);
      peerLeechers = R2ConnLeecher.FSM.multifsm(fsmIdFactory, peerLeecherES, oexa);
      torrents = R2Torrent.FSM.multifsm(fsmIdFactory, torrentES, oexa);
    } catch (FSMException ex) {
      throw new RuntimeException(ex);
    }
  }

  Handler handleStart = new Handler<Start>() {

    @Override
    public void handle(Start event) {
      LOG.info("{}starting", logPrefix);
      peerSeeders.setupHandlers();
      peerLeechers.setupHandlers();
      torrents.setupHandlers();
    }
  };

  //******************************************TESTING HELPERS***********************************************************

  FSMInternalState getConnSeederIS(Identifier baseId) {
    return peerSeeders.getFSMInternalState(baseId);
  }

  FSMStateName getConnSeederState(Identifier baseId) {
    return peerSeeders.getFSMState(baseId);
  }

  boolean activeSeederFSM(Identifier baseId) {
    return peerSeeders.activeFSM(baseId);
  }
  
  FSMStateName getConnLeecherState(Identifier baseId) {
    return peerLeechers.getFSMState(baseId);
  }
  
  boolean activeLeecherFSM(Identifier baseId) {
    return peerLeechers.activeFSM(baseId);
  }
  
  FSMStateName getTorrentState(Identifier baseId) {
    return torrents.getFSMState(baseId);
  }
  
  boolean activeTorrentFSM(Identifier baseId) {
    return torrents.activeFSM(baseId);
  }
  //********************************************************************************************************************

  public static class Ports {

    public final Positive<Network> network;
    public final Positive<Timer> timer;
    public final Negative<R2ConnSeederPort> seeders;
    public final Negative<R2ConnLeecherPort> leechers;
    public final Negative<R2TorrentPort> torrent;

    public Ports(ComponentProxy proxy) {
      network = proxy.requires(Network.class);
      timer = proxy.requires(Timer.class);
      seeders = proxy.provides(R2ConnSeederPort.class);
      leechers = proxy.provides(R2ConnLeecherPort.class);
      torrent = proxy.provides(R2TorrentPort.class);
    }
  }

  public static class Init extends se.sics.kompics.Init<R2MngrComp> {

    public final KAddress selfAdr;
    public final int retries = 5;
    public final long retryInterval = 1000;

    public Init(KAddress selfAdr) {
      this.selfAdr = selfAdr;
    }
  }
}
