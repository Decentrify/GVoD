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
package se.sics.silk.r2torrent.torrent;

import java.util.Random;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentProxy;
import se.sics.kompics.Port;
import se.sics.kompics.config.Config;
import se.sics.kompics.fsm.FSMException;
import se.sics.kompics.fsm.MultiFSM;
import se.sics.kompics.fsm.OnFSMExceptionAction;
import se.sics.kompics.fsm.id.FSMIdentifierFactory;
import se.sics.kompics.network.Network;
import se.sics.kompics.testing.TestContext;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.basic.IntIdFactory;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayIdFactory;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.silk.SystemHelper;
import se.sics.silk.SystemSetup;
import static se.sics.silk.TorrentTestHelper.eMetadataServeStopAck;
import static se.sics.silk.TorrentTestHelper.eMetadataServeSucc;
import static se.sics.silk.TorrentTestHelper.eNetMetadataServe;
import static se.sics.silk.TorrentTestHelper.tMetadataServeReq;
import static se.sics.silk.TorrentTestHelper.tMetadataServeStop;
import static se.sics.silk.TorrentTestHelper.tNetMetadataGet;
import se.sics.silk.TorrentWrapperComp;
import se.sics.silk.r2torrent.R2TorrentComp;
import se.sics.silk.r2torrent.R2TorrentPort;
import se.sics.silk.r2torrent.torrent.R1MetadataGet;
import se.sics.silk.r2torrent.torrent.R1MetadataServe;
import se.sics.silk.r2torrent.torrent.R1MetadataServe.States;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class R1MetadataServeTest {
  private TestContext<TorrentWrapperComp> tc;
  private Component comp;
  private TorrentWrapperComp compState;
  private Port<R2TorrentPort> triggerP;
  private Port<R2TorrentPort> expectP;
  private Port<Network> networkP;
  private static OverlayIdFactory torrentIdFactory;
  private IntIdFactory intIdFactory;
  private KAddress selfAdr;

  @BeforeClass
  public static void setup() throws FSMException {
    torrentIdFactory = SystemSetup.systemSetup("src/test/resources/application.conf");
  }

  @Before
  public void testSetup() {
    tc = getContext();
    comp = tc.getComponentUnderTest();
    compState = (TorrentWrapperComp) comp.getComponent();
    triggerP = comp.getNegative(R2TorrentPort.class);
    expectP = comp.getPositive(R2TorrentPort.class);
    networkP = comp.getNegative(Network.class);
    intIdFactory = new IntIdFactory(new Random());
  }

  private TestContext<TorrentWrapperComp> getContext() {
    selfAdr = SystemHelper.getAddress(0);
    TorrentWrapperComp.Setup setup = new TorrentWrapperComp.Setup() {
      @Override
      public MultiFSM setupFSM(ComponentProxy proxy, Config config, R2TorrentComp.Ports ports) {
        try {
          R1MetadataServe.ES fsmEs = new R1MetadataServe.ES();
          fsmEs.setProxy(proxy);
          fsmEs.setPorts(ports);
          
          OnFSMExceptionAction oexa = new OnFSMExceptionAction() {
            @Override
            public void handle(FSMException ex) {
              throw new RuntimeException(ex);
            }
          };
          FSMIdentifierFactory fsmIdFactory = config.getValue(FSMIdentifierFactory.CONFIG_KEY,
            FSMIdentifierFactory.class);
          return R1MetadataServe.FSM.multifsm(fsmIdFactory, fsmEs, oexa);
        } catch (FSMException ex) {
          throw new RuntimeException(ex);
        }
      }
    };
    TorrentWrapperComp.Init init = new TorrentWrapperComp.Init(selfAdr, setup);
    TestContext<TorrentWrapperComp> context = TestContext.newInstance(TorrentWrapperComp.class, init);
    return context;
  }

  @After
  public void clean() {
  }

  @Test
  public void testEmpty() {
    tc = tc.body();
    tc.repeat(1).body().end();
    assertTrue(tc.check());
  }
  
  //*************************************************START TO ACTIVE****************************************************
  @Test
  public void testStartToActive() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file0 = intIdFactory.id(new BasicBuilders.IntBuilder(0));
    tc = tc.body();
    tc = tMetadataServeReq(tc, triggerP, torrent, file0); //1
    tc = eMetadataServeSucc(tc, expectP); //2
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    Identifier fsmBaseId = R1MetadataGet.fsmBaseId(torrent, file0);
    assertEquals(States.ACTIVE, compState.fsm.getFSMState(fsmBaseId));
  }
  
  //************************************************ACTIVE TO ACTIVE****************************************************
  @Test
  public void testActiveServe() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file0 = intIdFactory.id(new BasicBuilders.IntBuilder(0));
    KAddress leecher = SystemHelper.getAddress(1);
    tc = tc.body();
    tc = tMetadataServeReq(tc, triggerP, torrent, file0); //1
    tc = eMetadataServeSucc(tc, expectP); //2
    tc = tNetMetadataGet(tc, networkP, leecher, selfAdr, torrent, file0);
    tc = eNetMetadataServe(tc, networkP, selfAdr, leecher);
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    Identifier fsmBaseId = R1MetadataGet.fsmBaseId(torrent, file0);
    assertEquals(States.ACTIVE, compState.fsm.getFSMState(fsmBaseId));
  }
  //*************************************************ACTIVE TO STOP*****************************************************
  @Test
  public void testActiveStop() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file0 = intIdFactory.id(new BasicBuilders.IntBuilder(0));
    tc = tc.body();
    tc = tMetadataServeReq(tc, triggerP, torrent, file0); //1
    tc = eMetadataServeSucc(tc, expectP); //2
    tc = tMetadataServeStop(tc, triggerP, torrent, file0); //3
    tc = eMetadataServeStopAck(tc, expectP); //4
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    Identifier fsmBaseId = R1MetadataServe.fsmBaseId(torrent, file0);
    assertFalse(compState.fsm.activeFSM(fsmBaseId));
  }
}