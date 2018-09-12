/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * KompicsToolbox is free software; you can redistribute it and/or
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
package se.sics.dela.storage.buffer.append;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.dela.storage.StreamStorage;
import se.sics.dela.storage.buffer.KBuffer;
import se.sics.dela.storage.buffer.KBufferConfig;
import se.sics.dela.storage.buffer.KBufferReport;
import se.sics.dela.storage.buffer.WriteCallback;
import se.sics.dela.storage.buffer.WriteResult;
import se.sics.kompics.ComponentProxy;
import se.sics.kompics.Handler;
import se.sics.kompics.Positive;
import se.sics.kompics.config.Config;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.reference.KReference;
import se.sics.ktoolbox.util.reference.KReferenceException;
import se.sics.ktoolbox.util.result.DelayedExceptionSyncHandler;
import se.sics.ktoolbox.util.result.Result;
import se.sics.nstream.StreamId;
import se.sics.nstream.storage.durable.DStoragePort;
import se.sics.nstream.storage.durable.events.DStorageWrite;
import se.sics.nstream.util.range.KBlock;

/**
 * The Buffer runs in the same component that calls its KBuffer methods. We
 * subscribe the handlers on the proxy of this component, thus they will run on
 * the same thread. There is no need for synchronization
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SimpleAppendKBuffer implements KBuffer {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleAppendKBuffer.class);
    private String logPrefix = "";

    private final KBufferConfig bufferConfig;
    private final Pair<StreamId, StreamStorage> stream;
    //**************************************************************************
    private final Positive<DStoragePort> writePort;
    private final ComponentProxy proxy;
    private final DelayedExceptionSyncHandler syncExHandling;
    //**************************************************************************
    private int blockPos;
    private int answeredBlockPos;
    private long appendPos;
    private final Map<Long, Pair<KReference<byte[]>, WriteCallback>> buffer = new HashMap<>();
    private final Set<Identifier> pendingWriteReqs = new HashSet<>(); 
    //**************************************************************************

    public SimpleAppendKBuffer(Config config, ComponentProxy proxy, DelayedExceptionSyncHandler syncExceptionHandling,
            Pair<StreamId, StreamStorage> stream, long appendPos) {
        this.bufferConfig = new KBufferConfig(config);
        this.stream = stream;
        this.proxy = proxy;
        this.syncExHandling = syncExceptionHandling;
        this.writePort = proxy.getNegative(DStoragePort.class).getPair();
        this.appendPos = appendPos;
        this.blockPos = 0;
        this.answeredBlockPos = 0;
        proxy.subscribe(handleWriteResp, writePort);
    }

    @Override
    public void start() {
        //nothing here
    }
    
    @Override
    public boolean isIdle() {
        return buffer.isEmpty();
    }

    @Override
    public void close() {
        proxy.unsubscribe(handleWriteResp, writePort);
        try {
            clean();
        } catch (KReferenceException ex) {
            syncExHandling.fail(Result.internalFailure(ex));
        }
    }
    
    @Override
    public void write(KBlock writeRange, KReference<byte[]> val, WriteCallback delayedWrite) {
        if(!val.retain()) {
            fail(Result.internalFailure(new IllegalStateException("buffer can't retain ref")));
            return;
        }
        buffer.put(writeRange.lowerAbsEndpoint(), Pair.with(val, delayedWrite));
        if (writeRange.lowerAbsEndpoint() == appendPos) {
            addNewTasks();
        }
    }

    private void addNewTasks() {
        while (true) {
            Pair<KReference<byte[]>, WriteCallback> next = buffer.get(appendPos);
            if (next == null) {
                break;
            }
            DStorageWrite.Request req = new DStorageWrite.Request(stream.getValue0(), appendPos, next.getValue0().getValue().get());
            pendingWriteReqs.add(req.eventId);
            proxy.trigger(req, writePort);
            appendPos += next.getValue0().getValue().get().length;
            blockPos++;
        }
    }

    Handler handleWriteResp = new Handler<DStorageWrite.Response>() {
        @Override
        public void handle(DStorageWrite.Response resp) {
            if(!pendingWriteReqs.remove(resp.getId())) {
                //not mine
                return;
            }
            LOG.debug("{}received:{}", logPrefix, resp);
            if (resp.result.isSuccess()) {
                answeredBlockPos++;
                Pair<KReference<byte[]>, WriteCallback> ref = buffer.remove(resp.req.pos);
                if(ref == null) {
                    LOG.error("{}pos:{}", logPrefix, resp.req.pos);
                    LOG.error("{}buf size:{}", logPrefix, buffer.size());
                    throw new RuntimeException("error");
                }
                try {
                    ref.getValue0().release();
                } catch (KReferenceException ex) {
                    fail(Result.internalFailure(ex));
                }
                WriteResult result = new WriteResult(stream, resp.req.pos, resp.req.value.length);
                ref.getValue1().success(Result.success(result));
                addNewTasks();
            } else {
                fail(resp.result);
            }
        }
    };

    private void fail(Result result) {
        Result cleanError = null;
        try {
            clean();
        } catch (KReferenceException ex) {
            cleanError = Result.internalFailure(ex);
        }
        syncExHandling.fail(result);
        if (cleanError != null) {
            syncExHandling.fail(cleanError);
        }
    }

    private void clean() throws KReferenceException {
        for (Pair<KReference<byte[]>, WriteCallback> ref : buffer.values()) {
            ref.getValue0().release();
        }
        buffer.clear();
    }

    @Override
    public KBufferReport report() {
        return new SimpleKBufferReport(blockPos, appendPos, buffer.size());
    }
}
