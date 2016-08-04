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
package se.sics.nstream.storage.buffer;

import java.util.HashMap;
import java.util.Map;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentProxy;
import se.sics.kompics.Handler;
import se.sics.kompics.Positive;
import se.sics.kompics.config.Config;
import se.sics.ktoolbox.util.reference.KReference;
import se.sics.ktoolbox.util.reference.KReferenceException;
import se.sics.ktoolbox.util.result.DelayedExceptionSyncHandler;
import se.sics.ktoolbox.util.result.Result;
import se.sics.nstream.storage.StoragePort;
import se.sics.nstream.storage.StorageWrite;
import se.sics.nstream.util.StreamEndpoint;
import se.sics.nstream.util.StreamResource;
import se.sics.nstream.util.range.KBlock;
import se.sics.nstream.util.result.WriteCallback;

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
    private final StreamResource writeResource;
    //**************************************************************************
    private final Positive<StoragePort> writePort;
    private final ComponentProxy proxy;
    private final DelayedExceptionSyncHandler syncExHandling;
    //**************************************************************************
    private int blockPos;
    private int answeredBlockPos;
    private long appendPos;
    private final Map<Long, Pair<KReference<byte[]>, WriteCallback>> buffer = new HashMap<>();
    //**************************************************************************

    public SimpleAppendKBuffer(Config config, ComponentProxy proxy, DelayedExceptionSyncHandler syncExceptionHandling, 
            StreamEndpoint writeEndpoint, StreamResource writeResource, long appendPos) {
        this.bufferConfig = new KBufferConfig(config);
        this.writeResource = writeResource;
        this.proxy = proxy;
        this.syncExHandling = syncExceptionHandling;
        this.writePort = proxy.getNegative(writeEndpoint.resourcePort()).getPair();
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
            proxy.trigger(new StorageWrite.Request(writeResource, appendPos, next.getValue0().getValue().get()), writePort);
            appendPos += next.getValue0().getValue().get().length;
            blockPos++;
        }
    }

    Handler handleWriteResp = new Handler<StorageWrite.Response>() {
        @Override
        public void handle(StorageWrite.Response resp) {
            LOG.debug("{}received:{}", logPrefix, resp);
            if(blockPos == 96) {
                int x = 1;
            }
            if(answeredBlockPos == 96) {
                int y = 1;
            }
            if (resp.result.isSuccess()) {
                answeredBlockPos++;
                Pair<KReference<byte[]>, WriteCallback> ref = buffer.remove(resp.req.pos);
                try {
                    ref.getValue0().release();
                } catch (KReferenceException ex) {
                    fail(Result.internalFailure(ex));
                }
                WriteResult result = new WriteResult(resp.req.pos, resp.req.value.length, writeResource.getResourceName());
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
