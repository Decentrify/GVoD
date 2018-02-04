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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentProxy;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.fsm.BaseIdExtractor;
import se.sics.kompics.fsm.FSMBasicStateNames;
import se.sics.kompics.fsm.FSMBuilder;
import se.sics.kompics.fsm.FSMEvent;
import se.sics.kompics.fsm.FSMException;
import se.sics.kompics.fsm.FSMInternalState;
import se.sics.kompics.fsm.FSMInternalStateBuilder;
import se.sics.kompics.fsm.FSMStateName;
import se.sics.kompics.fsm.MultiFSM;
import se.sics.kompics.fsm.OnFSMExceptionAction;
import se.sics.kompics.fsm.handler.FSMBasicEventHandler;
import se.sics.kompics.fsm.id.FSMIdentifier;
import se.sics.kompics.fsm.id.FSMIdentifierFactory;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.basic.IntIdFactory;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.silk.DefaultHandlers;
import se.sics.silk.event.SilkEvent;
import se.sics.silk.r2torrent.R2TorrentComp;
import se.sics.silk.r2torrent.R2TorrentCtrlPort;
import se.sics.silk.r2torrent.R2TorrentES;
import se.sics.silk.SelfPort;
import static se.sics.silk.r2torrent.torrent.R2Torrent.HardCodedConfig.seed;
import se.sics.silk.r2torrent.torrent.event.R1HashEvents;
import se.sics.silk.r2torrent.torrent.event.R1MetadataGetEvents;
import se.sics.silk.r2torrent.torrent.event.R1MetadataServeEvents;
import se.sics.silk.r2torrent.torrent.event.R2TorrentCtrlEvents;
import se.sics.silk.r2torrent.util.R2TorrentStatus;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class R2Torrent {

  private static final Logger LOG = LoggerFactory.getLogger(FSM.class);
  public static final String NAME = "dela-r2-torrent-fsm";

  public static class HackConfig {

    public final int seederOpenConn = 5;
    public final int filePerSeeder = 5;
  }

  public static enum States implements FSMStateName {

    META_GET,
    META_SERVE,
    DATA_STORAGE,
    HASH,
    UPLOAD,
    TRANSFER,
    CLEAN_META_GET,
    CLEAN_META_SERVE,
    CLEAN_HASH,
    CLEAN_UPLOAD,
    CLEAN_TRANSFER
  }

  public static interface Event extends FSMEvent, SilkEvent.TorrentEvent {
  }

  public static interface HashEvent extends Event {
  }

  public static interface MetadataEvent extends Event {

  }

  public static interface CtrlEvent extends Event {
  }

  public static Identifier fsmBaseId(OverlayId torrentId) {
    return torrentId;
  }

  public static class HardCodedConfig {

    public static final long seed = 1234;
  }

  public static class IS implements FSMInternalState {

    private final FSMIdentifier fsmId;
    OverlayId torrentId;
    final ISDataStorage dataStorage = new ISDataStorage();
    final ISSeederState seeders = new ISSeederState();
    final ISCtrlEvents ctrl = new ISCtrlEvents();

    public IS(FSMIdentifier fsmId) {
      this.fsmId = fsmId;
    }

    @Override
    public FSMIdentifier getFSMId() {
      return fsmId;
    }

    public void setGetMetaReq(R2TorrentCtrlEvents.MetaGetReq req) {
      torrentId = req.torrentId;
      seeders.setSample(req.partners);
      ctrl.getMetaReq = req;
    }

    public void setStopReq(R2TorrentCtrlEvents.Stop req) {
      ctrl.stopReq = req;
    }
  }

  public static class ISDataStorage {

    boolean set = false;
  }

  /**
   * TODO Alex - naive implementation for testing
   */
  static class ISSeederState {

    List<KAddress> seeders;
    KAddress metadataSeeder;

    Map<Identifier, ISSeederConn> openConn = new HashMap<>();

    public void setSample(List<KAddress> sample) {
      seeders = sample;
      metadataSeeder = seeders.get(0);
    }

    public KAddress getMetadataSeeder() {
      return metadataSeeder;
    }
  }

  static class ISSeederConn {

    KAddress seederAdr;
    Set<Integer> activeFiles = new TreeSet<>();
  }

  public static class ISCtrlEvents {

    private R2TorrentCtrlEvents.MetaGetReq getMetaReq;
    private R2TorrentCtrlEvents.Stop stopReq;
  }

  public static class ISBuilder implements FSMInternalStateBuilder {

    @Override
    public FSMInternalState newState(FSMIdentifier fsmId) {
      return new IS(fsmId);
    }
  }

  public static class ES implements R2TorrentES {

    public R2TorrentComp.Ports ports;
    private ComponentProxy proxy;
    public final IntIdFactory fileIdFactory;

    public ES() {
      this.fileIdFactory = new IntIdFactory(new Random(seed));
    }

    @Override
    public void setProxy(ComponentProxy proxy) {
      this.proxy = proxy;
    }

    @Override
    public ComponentProxy getProxy() {
      return proxy;
    }

    @Override
    public void setPorts(R2TorrentComp.Ports ports) {
      this.ports = ports;
    }
  }

  public static class FSM {

    private static FSMBuilder.StructuralDefinition structuralDef() throws FSMException {
      return FSMBuilder.structuralDef()
        .onStart()
        .nextStates(States.META_GET, States.META_SERVE)
        .buildTransition()
        .onState(States.META_GET)
        .nextStates(States.META_SERVE, States.CLEAN_META_GET)
        .toFinal()
        .buildTransition()
        .onState(States.META_SERVE)
        .nextStates(States.DATA_STORAGE, States.HASH, States.CLEAN_META_SERVE)
        .buildTransition()
        .onState(States.DATA_STORAGE)
        .nextStates(States.HASH, States.CLEAN_META_SERVE)
        .buildTransition()
        .onState(States.HASH)
        .nextStates(States.UPLOAD, States.TRANSFER, States.CLEAN_HASH, States.CLEAN_META_SERVE)
        .buildTransition()
        .onState(States.TRANSFER)
        .nextStates(States.CLEAN_META_SERVE)
        .buildTransition()
        .onState(States.UPLOAD)
        .nextStates(States.CLEAN_META_SERVE)
        .buildTransition()
        .onState(States.CLEAN_HASH)
        .nextStates(States.CLEAN_META_SERVE)
        .buildTransition()
        .onState(States.CLEAN_META_SERVE)
        .toFinal()
        .buildTransition()
        .onState(States.CLEAN_META_GET)
        .toFinal()
        .buildTransition();
    }

    private static FSMBuilder.SemanticDefinition semanticDef() throws FSMException {
      FSMBuilder.SemanticDefinition def = FSMBuilder.semanticDef()
        .defaultFallback(DefaultHandlers.basicDefault(), DefaultHandlers.patternDefault());
      def = def
        .negativePort(R2TorrentCtrlPort.class)
        .basicEvent(R2TorrentCtrlEvents.MetaGetReq.class)
        .subscribeOnStart(Handlers.getMetaReq)
        .basicEvent(R2TorrentCtrlEvents.Download.class)
        .subscribe(Handlers.download, States.DATA_STORAGE)
        .basicEvent(R2TorrentCtrlEvents.Upload.class)
        .subscribeOnStart(Handlers.upload)
        .basicEvent(R2TorrentCtrlEvents.Stop.class)
        .subscribe(Handlers.stop1, States.META_GET)
        .subscribe(Handlers.stop2, States.META_SERVE, States.DATA_STORAGE, States.UPLOAD, States.TRANSFER)
        .subscribe(Handlers.stop3, States.HASH)
        .buildEvents();
      def = def
        .positivePort(SelfPort.class)
        .basicEvent(R1MetadataGetEvents.GetSucc.class)
        .subscribe(Handlers.metaGetSucc, States.META_GET)
        .basicEvent(R1MetadataServeEvents.ServeSucc.class)
        .subscribe(Handlers.metaServeSucc, States.META_SERVE)
        .basicEvent(R1HashEvents.HashSucc.class)
        .subscribe(Handlers.hashSucc, States.HASH)
        .basicEvent(R1HashEvents.HashFail.class)
        .subscribe(Handlers.hashFail, States.HASH)
        .basicEvent(R1MetadataGetEvents.Stopped.class)
        .subscribe(Handlers.metaGetFail, States.META_GET)
        .subscribe(Handlers.metaGetClean, States.CLEAN_META_GET)
        .basicEvent(R1MetadataServeEvents.StopAck.class)
        .subscribe(Handlers.metaServeClean, States.CLEAN_META_SERVE)
        .basicEvent(R1HashEvents.HashStopAck.class)
        .subscribe(Handlers.cleanHash, States.CLEAN_HASH)
        .buildEvents();
      
      return def;
    }

    static BaseIdExtractor baseIdExtractor = new BaseIdExtractor() {

      @Override
      public Optional<Identifier> fromEvent(KompicsEvent event) throws FSMException {
        if (event instanceof Event) {
          Event e = (Event) event;
          return Optional.of(fsmBaseId(e.torrentId()));
        }
        return Optional.empty();
      }
    };

    public static MultiFSM multifsm(FSMIdentifierFactory fsmIdFactory, ES es, OnFSMExceptionAction oexa)
      throws FSMException {
      FSMInternalStateBuilder isb = new ISBuilder();
      return FSMBuilder.multiFSM(fsmIdFactory, NAME, structuralDef(), semanticDef(), es, isb, oexa, baseIdExtractor);
    }
  }

  public static class Handlers {

    //****************************************************CTRL**********************************************************
    static FSMBasicEventHandler stop1 = new FSMBasicEventHandler<ES, IS, R2TorrentCtrlEvents.Stop>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R2TorrentCtrlEvents.Stop req) {
        is.setStopReq(req);
        Identifier metaFileId = es.fileIdFactory.id(new BasicBuilders.IntBuilder(0));
        R1MetadataGetEvents.Stop r = new R1MetadataGetEvents.Stop(is.torrentId, metaFileId);
        sendMetaGet(es, r);
        return States.CLEAN_META_GET;
      }
    };

    static FSMBasicEventHandler stop2 = new FSMBasicEventHandler<ES, IS, R2TorrentCtrlEvents.Stop>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R2TorrentCtrlEvents.Stop req) {
        is.setStopReq(req);
        Identifier metaFileId = es.fileIdFactory.id(new BasicBuilders.IntBuilder(0));
        R1MetadataServeEvents.Stop r = new R1MetadataServeEvents.Stop(is.torrentId, metaFileId);
        sendMetaServe(es, r);
        return States.CLEAN_META_SERVE;
      }
    };
    
    static FSMBasicEventHandler stop3 = new FSMBasicEventHandler<ES, IS, R2TorrentCtrlEvents.Stop>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R2TorrentCtrlEvents.Stop req) {
        is.setStopReq(req);
        R1HashEvents.HashStop r = new R1HashEvents.HashStop(is.torrentId);
        sendHash(es, r);
        return States.CLEAN_HASH;
      }
    };

    static FSMBasicEventHandler getMetaReq = new FSMBasicEventHandler<ES, IS, R2TorrentCtrlEvents.MetaGetReq>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R2TorrentCtrlEvents.MetaGetReq req) {
        is.setGetMetaReq(req);
        Identifier metaFileId = es.fileIdFactory.id(new BasicBuilders.IntBuilder(0));
        R1MetadataGetEvents.GetReq r = new R1MetadataGetEvents.GetReq(is.torrentId, metaFileId, 
          is.seeders.getMetadataSeeder());
        sendMetaGet(es, r);
        return States.META_GET;
      }
    };

    static FSMBasicEventHandler download = new FSMBasicEventHandler<ES, IS, R2TorrentCtrlEvents.Download>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R2TorrentCtrlEvents.Download req) {
        is.dataStorage.set = true;
        R1HashEvents.HashReq r = new R1HashEvents.HashReq(is.torrentId);
        sendHash(es, r);
        return States.HASH;
      }
    };

    static FSMBasicEventHandler upload = new FSMBasicEventHandler<ES, IS, R2TorrentCtrlEvents.Upload>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R2TorrentCtrlEvents.Upload req) {
        is.torrentId = req.torrentId;
        is.dataStorage.set = true;
        Identifier metaFileId = es.fileIdFactory.id(new BasicBuilders.IntBuilder(0));
        R1MetadataServeEvents.ServeReq r = new R1MetadataServeEvents.ServeReq(is.torrentId, metaFileId);
        sendMetaServe(es, r);
        return States.META_SERVE;
      }
    };
    //****************************************************TRANSFER******************************************************
    static FSMBasicEventHandler metaGetSucc = new FSMBasicEventHandler<ES, IS, R1MetadataGetEvents.GetSucc>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R1MetadataGetEvents.GetSucc resp) {
        sendCtrl(es, is.ctrl.getMetaReq.success());
        R1MetadataServeEvents.ServeReq r = new R1MetadataServeEvents.ServeReq(is.torrentId, resp.fileId);
        sendMetaServe(es, r);
        return States.META_SERVE;
      }
    };

    static FSMBasicEventHandler metaServeSucc
      = new FSMBasicEventHandler<ES, IS, R1MetadataServeEvents.ServeSucc>() {
        @Override
        public FSMStateName handle(FSMStateName state, ES es, IS is, R1MetadataServeEvents.ServeSucc resp) {
          LOG.info("META-SERVE success - processing");
          sendCtrl(es, new R2TorrentCtrlEvents.TorrentBaseInfo(is.torrentId, R2TorrentStatus.META_SERVE));
          if (is.dataStorage.set) {
            R1HashEvents.HashReq r = new R1HashEvents.HashReq(is.torrentId);
            sendHash(es, r);
            LOG.info("META-SERVE success - processed");
            return States.HASH;
          } else {
            return States.DATA_STORAGE;
          }
        }
      };

    static FSMBasicEventHandler hashSucc = new FSMBasicEventHandler<ES, IS, R1HashEvents.HashSucc>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R1HashEvents.HashSucc resp) {
        sendCtrl(es, new R2TorrentCtrlEvents.TorrentBaseInfo(is.torrentId, R2TorrentStatus.HASH));
        return States.TRANSFER;
      }
    };
    
    
    static FSMBasicEventHandler metaGetFail = new FSMBasicEventHandler<ES, IS, R1MetadataGetEvents.Stopped>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R1MetadataGetEvents.Stopped resp) {
        sendCtrl(es, is.ctrl.getMetaReq.fail());
        return FSMBasicStateNames.FINAL;
      }
    };
    
    static FSMBasicEventHandler metaGetClean = new FSMBasicEventHandler<ES, IS, R1MetadataGetEvents.Stopped>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R1MetadataGetEvents.Stopped ack) {
        if (is.ctrl.stopReq != null) {
          sendCtrl(es, is.ctrl.stopReq.ack());
        }
        return FSMBasicStateNames.FINAL;
      }
    };
    
    static FSMBasicEventHandler metaServeClean = new FSMBasicEventHandler<ES, IS, R1MetadataServeEvents.StopAck>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R1MetadataServeEvents.StopAck ack) {
        if (is.ctrl.stopReq != null) {
          sendCtrl(es, is.ctrl.stopReq.ack());
        }
        return FSMBasicStateNames.FINAL;
      }
    };
    
    static FSMBasicEventHandler hashFail = new FSMBasicEventHandler<ES, IS, R1HashEvents.HashFail>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R1HashEvents.HashFail resp) {
        sendCtrl(es, new R2TorrentCtrlEvents.TorrentBaseInfo(is.torrentId, R2TorrentStatus.ERROR));
        Identifier metaFileId = es.fileIdFactory.id(new BasicBuilders.IntBuilder(0));
        R1MetadataServeEvents.Stop r = new R1MetadataServeEvents.Stop(is.torrentId, metaFileId);
        sendMetaServe(es, r);
        return States.CLEAN_META_SERVE;
      }
    };

    static FSMBasicEventHandler cleanHash = new FSMBasicEventHandler<ES, IS, R1HashEvents.HashStopAck>() {
      @Override
      public FSMStateName handle(FSMStateName state, ES es, IS is, R1HashEvents.HashStopAck ack) {
        Identifier metaFileId = es.fileIdFactory.id(new BasicBuilders.IntBuilder(0));
        R1MetadataServeEvents.Stop r = new R1MetadataServeEvents.Stop(is.torrentId, metaFileId);
        sendMetaServe(es, r);
        return States.CLEAN_META_SERVE;
      }
    };

    private static void sendMetaGet(ES es, R1MetadataGet.TorrentEvent e) {
      es.getProxy().trigger(e, es.ports.loopbackPos);
    }

    private static void sendMetaServe(ES es, R1MetadataServe.TorrentEvent e) {
      es.getProxy().trigger(e, es.ports.loopbackPos);
    }

    private static void sendHash(ES es, R1Hash.TorrentEvent e) {
      es.getProxy().trigger(e, es.ports.loopbackPos);
    }

    private static void sendCtrl(ES es, CtrlEvent e) {
      es.getProxy().trigger(e, es.ports.ctrl);
    }
  }
}
