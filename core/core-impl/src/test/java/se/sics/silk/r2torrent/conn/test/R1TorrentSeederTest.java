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
package se.sics.silk.r2torrent.conn.test;

import java.util.Random;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import se.sics.kompics.Component;
import se.sics.kompics.Port;
import se.sics.kompics.fsm.FSMException;
import se.sics.kompics.testing.Future;
import se.sics.kompics.testing.TestContext;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.basic.IntIdFactory;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayIdFactory;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.silk.SystemHelper;
import se.sics.silk.SystemSetup;
import static se.sics.silk.TorrentTestHelper.torrentSeederConnSucc;
import se.sics.silk.r2torrent.R2TorrentPort;
import se.sics.silk.r2torrent.conn.R1TorrentSeeder.States;
import se.sics.silk.r2torrent.conn.helper.R1TorrentSeederAuxComp;
import static se.sics.silk.r2torrent.conn.helper.R1TorrentSeederHelper.torrentSeederConnFail;
import static se.sics.silk.r2torrent.conn.helper.R1TorrentSeederHelper.torrentSeederConnReq;
import static se.sics.silk.r2torrent.conn.helper.R1TorrentSeederHelper.torrentSeederDisconnect;
import static se.sics.silk.r2torrent.conn.helper.R2NodeSeederHelper.nodeConnFailLoc;
import static se.sics.silk.r2torrent.conn.helper.R2NodeSeederHelper.nodeConnSuccLoc;
import static se.sics.silk.r2torrent.conn.helper.R2NodeSeederHelper.nodeSeederConnFailLoc;
import static se.sics.silk.r2torrent.conn.helper.R2NodeSeederHelper.nodeSeederConnReqLoc;
import static se.sics.silk.r2torrent.conn.helper.R2NodeSeederHelper.nodeSeederConnSuccLoc;
import static se.sics.silk.r2torrent.conn.helper.R2NodeSeederHelper.nodeSeederDisconnectLoc;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class R1TorrentSeederTest {

  private TestContext<R1TorrentSeederAuxComp> tc;
  private Component comp;
  private R1TorrentSeederAuxComp compState;
  private Port<R2TorrentPort> triggerP;
  private Port<R2TorrentPort> expectP;
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
    compState = (R1TorrentSeederAuxComp) comp.getComponent();
    triggerP = comp.getNegative(R2TorrentPort.class);
    expectP = comp.getPositive(R2TorrentPort.class);
    intIdFactory = new IntIdFactory(new Random());
  }

  private TestContext<R1TorrentSeederAuxComp> getContext() {
    selfAdr = SystemHelper.getAddress(0);
    R1TorrentSeederAuxComp.Init init = new R1TorrentSeederAuxComp.Init(selfAdr);
    TestContext<R1TorrentSeederAuxComp> context = TestContext.newInstance(R1TorrentSeederAuxComp.class, init);
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

  //*********************************************CONNECT TO CONNECT*****************************************************
  @Test
  public void testNewConnect1() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file2 = intIdFactory.id(new BasicBuilders.IntBuilder(2));

    tc = tc.body();
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file1, seeder));//2
    tc = nodeSeederConnReqLoc(tc, expectP);//3
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file2, seeder));//4
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertEquals(States.CONNECT, compState.seederState(torrent, seeder));
  }

  @Test
  public void testDisconnect1() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file2 = intIdFactory.id(new BasicBuilders.IntBuilder(2));

    tc = tc.body();
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file1, seeder));//2
    tc = nodeSeederConnReqLoc(tc, expectP);//3
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file2, seeder)); //4
    tc = torrentSeederDisconnect(tc, triggerP, torrent, file2, seeder);//5
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertEquals(States.CONNECT, compState.seederState(torrent, seeder));
  }

  //********************************************CONNECT TO CONNECTED****************************************************

  @Test
  public void testConnected() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));

    tc = tc.body();
    tc = connected(tc, torrent, file1, seeder); //2-5
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertEquals(States.CONNECTED, compState.seederState(torrent, seeder));
  }

  private TestContext connected(TestContext tc, OverlayId torrentId, Identifier fileId, KAddress seeder) {
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrentId, fileId, seeder));//1 
    tc = nodeSeederConnSuccLoc(tc, expectP, triggerP);//2-3
    tc = torrentSeederConnSucc(tc, expectP);//4
    return tc;
  }

  //*******************************************CONNECTED TO CONNECTED***************************************************

  @Test
  public void testNewConnect2() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file2 = intIdFactory.id(new BasicBuilders.IntBuilder(2));

    tc = tc.body();
    tc = connected(tc, torrent, file1, seeder); //2-5
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file2, seeder));//6
    tc = torrentSeederConnSucc(tc, expectP);//7
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertEquals(States.CONNECTED, compState.seederState(torrent, seeder));
  }

  @Test
  public void testDisconnect2() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file2 = intIdFactory.id(new BasicBuilders.IntBuilder(2));

    tc = tc.body();
    tc = connected(tc, torrent, file1, seeder); //2-5
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file2, seeder)); //6
    tc = torrentSeederConnSucc(tc, expectP);//7
    tc = torrentSeederDisconnect(tc, triggerP, torrent, file2, seeder);//8
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertEquals(States.CONNECTED, compState.seederState(torrent, seeder));
  }

  //*********************************************START TO CONNECTED*****************************************************

  @Test
  public void testNewConnect3() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file2 = intIdFactory.id(new BasicBuilders.IntBuilder(2));

    Future nodeConnSucc = nodeConnSuccLoc();
    tc = tc.body();
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file1, seeder));//2
    tc = nodeSeederConnReqLoc(tc, expectP, nodeConnSucc);//3
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file2, seeder));//4
    tc = nodeSeederConnSuccLoc(tc, triggerP, nodeConnSucc); //5
    tc = tc.unordered();
    tc = torrentSeederConnSucc(tc, expectP);//6
    tc = torrentSeederConnSucc(tc, expectP);//6
    tc = tc.end();
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertEquals(States.CONNECTED, compState.seederState(torrent, seeder));
  }

  //********************************************CONNECT TO DISCONNECT***************************************************

  @Test
  public void testConnFail1() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file2 = intIdFactory.id(new BasicBuilders.IntBuilder(2));

    Future disc = nodeConnFailLoc();
    tc = tc.body();
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file1, seeder));//2
    tc = nodeSeederConnReqLoc(tc, expectP, disc);//3
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file2, seeder));//4
    tc = nodeSeederConnFailLoc(tc, triggerP, disc); //5
    tc = tc.unordered();
    tc = torrentSeederConnFail(tc, expectP); //6
    tc = torrentSeederConnFail(tc, expectP); //6
    tc = tc.end();
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertFalse(compState.activeSeederFSM(torrent, seeder));
  }

  @Test
  public void testLocDisconnect1() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));

    tc = tc.body();
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file1, seeder));//2
    tc = nodeSeederConnReqLoc(tc, expectP);//3
    tc = torrentSeederDisconnect(tc, triggerP, torrent, file1, seeder); //4
    tc = nodeSeederDisconnectLoc(tc, expectP); //5
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertFalse(compState.activeSeederFSM(torrent, seeder));
  }

  //********************************************CONNECTED TO DISCONNECT*************************************************

  @Test
  public void testConnFail2() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));
    Identifier file2 = intIdFactory.id(new BasicBuilders.IntBuilder(2));

    tc = tc.body();
    tc = connected(tc, torrent, file1, seeder); //2-5
    tc = torrentSeederConnReq(tc, triggerP, torrentSeederConnReq(torrent, file2, seeder));//6
    tc = torrentSeederConnSucc(tc, expectP); //7
    tc = nodeSeederConnFailLoc(tc, triggerP, nodeSeederConnFailLoc(torrent, seeder.getId()));//8
    tc = tc.unordered();
    tc = torrentSeederConnFail(tc, expectP);//9
    tc = torrentSeederConnFail(tc, expectP);//9
    tc = tc.end();
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertFalse(compState.activeSeederFSM(torrent, seeder));
  }

  @Test
  public void testLocDisconnect2() {
    OverlayId torrent = torrentIdFactory.id(new BasicBuilders.IntBuilder(1));
    KAddress seeder = SystemHelper.getAddress(1);
    Identifier file1 = intIdFactory.id(new BasicBuilders.IntBuilder(1));

    tc = tc.body();
    tc = connected(tc, torrent, file1, seeder);//2-5
    tc = torrentSeederDisconnect(tc, triggerP, torrent, file1, seeder); //6
    tc = nodeSeederDisconnectLoc(tc, expectP); //7
    tc.repeat(1).body().end();
    assertTrue(tc.check());
    assertFalse(compState.activeSeederFSM(torrent, seeder));
  }
}