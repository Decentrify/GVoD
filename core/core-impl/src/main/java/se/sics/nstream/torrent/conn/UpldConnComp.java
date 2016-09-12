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
package se.sics.nstream.torrent.conn;

import se.sics.nstream.torrent.FileIdentifier;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.ktoolbox.util.network.KContentMsg;
import se.sics.ktoolbox.util.network.KHeader;
import se.sics.ktoolbox.util.reference.KReference;
import se.sics.ktoolbox.util.reference.KReferenceException;
import se.sics.ktoolbox.util.tracking.load.NetworkQueueLoadProxy;
import se.sics.ktoolbox.util.tracking.load.QueueLoadConfig;
import se.sics.ledbat.ncore.msg.LedbatMsg;
import se.sics.nstream.torrent.conn.msg.CacheHint;
import se.sics.nstream.torrent.conn.msg.DownloadPiece;
import se.sics.nstream.torrent.conn.upld.event.GetBlocks;
import se.sics.nstream.torrent.conn.upld.event.UpldConnReport;
import se.sics.nstream.transfer.BlockHelper;
import se.sics.nstream.util.FileBaseDetails;
import se.sics.nstream.util.range.KPiece;
import se.sics.nstream.util.range.RangeKReference;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class UpldConnComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(UpldConnComp.class);
    private String logPrefix;

    private static final long REPORT_PERIOD = 1000;
    //**************************************************************************
    Negative<UpldConnPort> connPort = provides(UpldConnPort.class);
    Positive<Network> networkPort = requires(Network.class);
    Positive<Timer> timerPort = requires(Timer.class);
    //**************************************************************************
    private final FileIdentifier fileId;
    private final KAddress self;
    private final KAddress target;
    private final FileBaseDetails fileDetails;
    //**************************************************************************
    private final NetworkQueueLoadProxy networkQueueLoad;
    private final Map<Integer, KReference<byte[]>> servedBlocks = new HashMap<>();
    private KContentMsg<?, ?, CacheHint.Request> pendingCacheReq;
    //**************************************************************************
    private UUID reportTid;

    public UpldConnComp(Init init) {
        this.fileId = init.fileId;
        this.self = init.self;
        this.target = init.target;
        this.fileDetails = init.fileDetails;
        logPrefix = "<nid:" + self.getId() + ",cid:" + fileId + ">";

        
        networkQueueLoad = new NetworkQueueLoadProxy(logPrefix, proxy, new QueueLoadConfig(config()));
        subscribe(handleStart, control);
        subscribe(handleReport, timerPort);
        subscribe(handleCache, networkPort);
        subscribe(handleGetBlocks, connPort);
        subscribe(handlePiece, networkPort);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting conn to:{}", logPrefix, target);
            networkQueueLoad.start();
            scheduleReport();
        }
    };

    @Override
    public void tearDown() {
        networkQueueLoad.tearDown();
        cancelReport();
        for (KReference<byte[]> block : servedBlocks.values()) {
            silentRelease(block);
        }
    }
    
    Handler handleReport = new Handler<ReportTimeout>() {
        @Override
        public void handle(ReportTimeout event) {
            double queueAdjustment = networkQueueLoad.adjustment();
            Pair<Integer, Integer> queueDelay = networkQueueLoad.queueDelay();
            trigger(new UpldConnReport(fileId, target, queueDelay, queueAdjustment), connPort);
        }
    };
    
    //**************************************************************************
    ClassMatchedHandler handleCache
            = new ClassMatchedHandler<CacheHint.Request, KContentMsg<KAddress, KHeader<KAddress>, CacheHint.Request>>() {

                @Override
                public void handle(CacheHint.Request content, KContentMsg<KAddress, KHeader<KAddress>, CacheHint.Request> context) {
                    if (pendingCacheReq == null) {
                        pendingCacheReq = context;
                        Set<Integer> newCache = Sets.difference(content.requestCache.blocks, servedBlocks.keySet());
                        Set<Integer> delCache = Sets.difference(servedBlocks.keySet(), content.requestCache.blocks);

                        trigger(new GetBlocks.Request(fileId, target, newCache), connPort);

                        //release references that were retained when given to us
                        for (Integer blockNr : delCache) {
                            KReference<byte[]> block = servedBlocks.remove(blockNr);
                            silentRelease(block);
                        }
                    }
                }
            };

    Handler handleGetBlocks = new Handler<GetBlocks.Response>() {
        @Override
        public void handle(GetBlocks.Response resp) {
            //references are already retained by whoever gives them to us
            servedBlocks.putAll(resp.blocks);
            answerMsg(pendingCacheReq, pendingCacheReq.getContent().success());
            pendingCacheReq = null;
        }
    };
    //**************************************************************************
    ClassMatchedHandler handlePiece
            = new ClassMatchedHandler<LedbatMsg.Request<DownloadPiece.Request>, KContentMsg<KAddress, KHeader<KAddress>, LedbatMsg.Request<DownloadPiece.Request>>>() {

                @Override
                public void handle(LedbatMsg.Request<DownloadPiece.Request> content, KContentMsg<KAddress, KHeader<KAddress>, LedbatMsg.Request<DownloadPiece.Request>> context) {
                    int blockNr = content.payload.piece.getValue0();
                    KReference<byte[]> block = servedBlocks.get(blockNr);
                    if(block == null) {
                        throw new RuntimeException("bad cache-block logic");
                    }
                    //retain block here - release in serializer
                    if(!block.retain()) {
                        throw new RuntimeException("bad ref logic");
                    }
                    KPiece pieceRange = BlockHelper.getPieceRange(content.payload.piece, fileDetails);
                    RangeKReference piece = RangeKReference.createInstance(block, blockNr, pieceRange);
                    LedbatMsg.Response respContent = content.answer(content.payload.success(piece));
                    answerMsg(context, respContent);
                }
            };
    //**************************************************************************

    private void answerMsg(KContentMsg original, KompicsEvent respContent) {
        trigger(original.answer(respContent), networkPort);
    }

    private void silentRelease(KReference<byte[]> ref) {
        try {
            ref.release();
        } catch (KReferenceException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static class Init extends se.sics.kompics.Init<UpldConnComp> {

        public final FileIdentifier fileId;
        public final KAddress self;
        public final KAddress target;
        public final FileBaseDetails fileDetails;

        public Init(FileIdentifier fileId, KAddress self, KAddress target, FileBaseDetails fileDetails) {
            this.fileId = fileId;
            this.self = self;
            this.target = target;
            this.fileDetails = fileDetails;
        }
    }
    
    private void scheduleReport() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(REPORT_PERIOD, REPORT_PERIOD);
        ReportTimeout rt = new ReportTimeout(spt);
        spt.setTimeoutEvent(rt);
        reportTid = rt.getTimeoutId();
        trigger(spt, timerPort);
    }

    private void cancelReport() {
        CancelPeriodicTimeout cpd = new CancelPeriodicTimeout(reportTid);
        trigger(cpd, timerPort);
        reportTid = null;
    }
    
    public static class ReportTimeout extends Timeout {
        public ReportTimeout(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }
}
