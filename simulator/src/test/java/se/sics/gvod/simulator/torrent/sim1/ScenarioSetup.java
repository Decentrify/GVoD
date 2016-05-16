/*
 * 2016 Royal Institute of Technology (KTH)
 *
 * LSelector is free software; you can redistribute it and/or
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
package se.sics.gvod.simulator.torrent.sim1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.gvod.common.util.VodDescriptor;
import se.sics.gvod.core.util.TorrentDetails;
import se.sics.gvod.simulator.TestDriver;
import se.sics.gvod.simulator.torrent.sim1.TorrentDriver;
import se.sics.gvod.simulator.torrent.sim1.TorrentDriverComp.NetworkDelay;
import se.sics.gvod.stream.connection.event.Connection;
import se.sics.gvod.stream.torrent.event.DownloadStatus;
import se.sics.gvod.stream.torrent.event.TorrentGet;
import se.sics.kompics.ComponentProxy;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.network.Network;
import se.sics.kompics.simulator.util.GlobalView;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.util.identifiable.Identifier;
import se.sics.ktoolbox.util.identifiable.basic.IntIdentifier;
import se.sics.ktoolbox.util.identifiable.basic.OverlayIdFactory;
import se.sics.ktoolbox.util.managedStore.core.FileMngr;
import se.sics.ktoolbox.util.managedStore.core.HashMngr;
import se.sics.ktoolbox.util.managedStore.core.ManagedStoreHelper;
import se.sics.ktoolbox.util.managedStore.core.impl.StorageMngrFactory;
import se.sics.ktoolbox.util.managedStore.core.impl.TransferMngr;
import se.sics.ktoolbox.util.managedStore.core.util.FileInfo;
import se.sics.ktoolbox.util.managedStore.core.util.HashUtil;
import se.sics.ktoolbox.util.managedStore.core.util.Torrent;
import se.sics.ktoolbox.util.managedStore.core.util.TorrentInfo;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.ktoolbox.util.network.KContentMsg;
import se.sics.ktoolbox.util.network.KHeader;
import se.sics.ktoolbox.util.network.basic.BasicAddress;
import se.sics.ktoolbox.util.network.basic.BasicContentMsg;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarioSetup {

    public static final long scenarioSeed = 1234;
    private static final int appPort = 12345;

    private static final byte overlayOwner = 0x10;
    private static final Identifier overlayId;

    static {
        overlayId = OverlayIdFactory.getId(overlayOwner, OverlayIdFactory.Type.TGRADIENT, new byte[]{0, 0, 1});
    }

    public static final int startingUploaderId = 1;
    public static final int nrUploaders = 2;
    public static final int startingDownloaderId = 100;
    public static final int nrDownloaders = 1;
    private static final Set<Integer> uploaderIds = new HashSet<>();
    private static final Map<Integer, Pair<Integer, Integer>> uploaderParam = new HashMap<>();
    static {
        for (int i = 0; i < nrUploaders; i++) {
            uploaderIds.add(startingUploaderId + i);
            uploaderParam.put(startingUploaderId + i, Pair.with((i+1)*10, (i+1)*100));
        }
    }

    private static final String torrentName = "test.txt";
    private static final String testDir = "src/test/resources/torrent/sim1";
    private static final String successFilePath = testDir + "/success";
    private static final String uploadFilePath = testDir + "/upload/" + torrentName;
    private static final String uploadHashPath = testDir + "/upload/" + torrentName + ".hash";
    private static final String downloadFilePath = testDir + "/download/" + torrentName;
    private static final String downloadHashPath = testDir + "/download/" + torrentName + ".hash";
    private static final Torrent torrent = new Torrent(overlayId, FileInfo.newFile(torrentName, 100 * 1024 * 1024 + 5 * 1024 + 5), new TorrentInfo(1024, 1024, HashUtil.getAlgName(HashUtil.SHA), 2 * 20));

    private static void success() throws IOException {
        File f = new File(successFilePath);
        f.createNewFile();
    }

    public static boolean checkSuccess() {
        File f = new File(successFilePath);
        return f.exists();
    }

    public static void start() throws IOException, HashUtil.HashBuilderException {
        setup();
        cleanup();
        generate();
    }

    private static void setup() {
        File dir = new File(testDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File upDir = new File(testDir + "/upload");
        if (!upDir.exists()) {
            upDir.mkdir();
        }
        File downDir = new File(testDir + "/download");
        if (!downDir.exists()) {
            downDir.mkdir();
        }
    }

    public static void cleanup() {
        File successFile = new File(successFilePath);
        if (successFile.exists()) {
            successFile.delete();
        }
        File uploadFile = new File(uploadFilePath);
        if (uploadFile.exists()) {
            uploadFile.delete();
        }
        File uploadHash = new File(uploadHashPath);
        if (uploadHash.exists()) {
            uploadHash.delete();
        }
        File downloadFile = new File(downloadFilePath);
        if (downloadFile.exists()) {
            downloadFile.delete();
        }
        File downloadHash = new File(downloadHashPath);
        if (downloadHash.exists()) {
            downloadHash.delete();
        }
    }

    private static void generate() throws IOException, HashUtil.HashBuilderException {
        Random rand = new Random(scenarioSeed);
        File uploadFile = new File(uploadFilePath);
        uploadFile.createNewFile();
        FileOutputStream fileOut = new FileOutputStream(uploadFile);
        for (int i = 0; i < torrent.fileInfo.size / 1024; i++) {
            byte[] data = new byte[1024];
            rand.nextBytes(data);
            fileOut.write(data);
        }
        if (torrent.fileInfo.size % 1024 != 0) {
            byte[] data = new byte[(int) torrent.fileInfo.size % 1024];
            rand.nextBytes(data);
            fileOut.write(data);
        }
        fileOut.flush();
        fileOut.close();

        HashUtil.makeHashes(uploadFilePath, uploadHashPath, torrent.torrentInfo.hashAlg, torrent.torrentInfo.pieceSize * torrent.torrentInfo.piecesPerBlock);
    }

    private static final TorrentDetails uploadTorrentDetails = new TorrentDetails() {

        @Override
        public Identifier getOverlayId() {
            return overlayId;
        }

        @Override
        public boolean download() {
            return false;
        }

        @Override
        public Torrent getTorrent() {
            return torrent;
        }

        @Override
        public Triplet<FileMngr, HashMngr, TransferMngr> torrentMngrs(Torrent torrent) {
            int blockSize = torrent.torrentInfo.pieceSize * torrent.torrentInfo.piecesPerBlock;
            String hashAlg = torrent.torrentInfo.hashAlg;
            int hashSize = HashUtil.getHashSize(hashAlg);
            long hashFileSize = torrent.fileInfo.size % blockSize == 0 ? (torrent.fileInfo.size / blockSize) * hashSize : (torrent.fileInfo.size / blockSize + 1) * hashSize;
            try {
                FileMngr fileMngr = StorageMngrFactory.completeMMFileMngr(uploadFilePath, torrent.fileInfo.size, blockSize, torrent.torrentInfo.pieceSize);
                HashMngr hashMngr = StorageMngrFactory.completeMMHashMngr(uploadHashPath, hashAlg, hashFileSize, hashSize);
                TransferMngr transferMngr = null;
                return Triplet.with(fileMngr, hashMngr, transferMngr);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    };

    private static final TorrentDetails downloadTorrentDetails = new TorrentDetails() {

        @Override
        public Identifier getOverlayId() {
            return overlayId;
        }

        @Override
        public boolean download() {
            return true;
        }

        @Override
        public Torrent getTorrent() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Triplet<FileMngr, HashMngr, TransferMngr> torrentMngrs(Torrent torrent) {
            int blockSize = torrent.torrentInfo.pieceSize * torrent.torrentInfo.piecesPerBlock;
            String hashAlg = torrent.torrentInfo.hashAlg;
            int hashSize = HashUtil.getHashSize(hashAlg);
            long hashFileSize = torrent.fileInfo.size % blockSize == 0 ? (torrent.fileInfo.size / blockSize) * hashSize : (torrent.fileInfo.size / blockSize + 1) * hashSize;
            try {
                FileMngr fileMngr = StorageMngrFactory.incompleteMMFileMngr(downloadFilePath, torrent.fileInfo.size, blockSize, torrent.torrentInfo.pieceSize);
                HashMngr hashMngr = StorageMngrFactory.incompleteMMHashMngr(downloadHashPath, hashAlg, hashFileSize, hashSize);
                TransferMngr transferMngr = new TransferMngr(torrent, hashMngr, fileMngr);
                return Triplet.with(fileMngr, hashMngr, transferMngr);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    };

    public final static TorrentDriver dwnlTorrentDriver(final int nodeId) {
        return new TorrentDriver() {
            private final Logger LOG = LoggerFactory.getLogger(TestDriver.class);

            final KAddress selfAdr = getNodeAdr(nodeId);
            final TorrentDetails torrentDetails = downloadTorrentDetails;
            final Random rand = new Random(getNodeSeed(nodeId));
            int state = 1;

            @Override
            public KAddress getSelfAdr() {
                return selfAdr;
            }

            @Override
            public TorrentDetails getTorrentDetails() {
                return torrentDetails;
            }

            @Override
            public void next(ComponentProxy proxy, GlobalView gv, KompicsEvent event) {
                if (state == 1 && event instanceof Connection.Request) {
                    LOG.info("dwnl state1 - publish connections");
                    state1(proxy, gv, (Connection.Request) event);
                    state++;
                    return;
                }
                if (state == 2 && event instanceof BasicContentMsg) {
                    BasicContentMsg<KAddress, KHeader<KAddress>, Object> msg = (BasicContentMsg) event;
                    if (msg.getHeader().getSource().getId().equals(selfAdr.getId())) {
                        LOG.info("dwnl state2 - outgoing traffic");
                        proxy.trigger(event, proxy.getNegative(Network.class).getPair());
                    } else if (msg.getHeader().getDestination().getId().equals(selfAdr.getId())) {
                        LOG.info("dwnl state2 - incoming traffic");
                        proxy.trigger(event, proxy.getPositive(Network.class).getPair());
                    }
                    return;
                }
                if (state == 2 && event instanceof DownloadStatus.Done) {
                    state++;
                    LOG.info("dwnl state3 - finished download");
                    try {
                        success();
                        gv.terminate();
                        return;
                    } catch (IOException ex) {
                        gv.terminate();
                        throw new RuntimeException(ex);
                    }
                }
                gv.terminate();
                throw new RuntimeException("state" + state + " illegal event:" + event.getClass().getCanonicalName());
            }

            private void state1(ComponentProxy proxy, GlobalView gv, Connection.Request req) {
                Map<Identifier, KAddress> connections = new HashMap();
                Map<Identifier, VodDescriptor> descriptors = new HashMap();

                for (Integer partnerId : uploaderIds) {
                    KAddress adr = getNodeAdr(partnerId);
                    connections.put(adr.getId(), adr);
                    descriptors.put(adr.getId(), new VodDescriptor(-1));
                }

                proxy.answer(req, req.answer(connections, descriptors));
            }
        };
    }

    public final static TorrentDriver upldTorrentDriver(final int nodeId) {
        return new TorrentDriver() {
            private final Logger LOG = LoggerFactory.getLogger(TestDriver.class);

            final KAddress selfAdr = getNodeAdr(nodeId);
            final Random rand = new Random(getNodeSeed(nodeId));
            final TorrentDetails torrentDetails = uploadTorrentDetails;
            int state = 1;
            Pair<Integer, Integer> delayParam;
            int pendingMsg = 0;

            {
                delayParam = uploaderParam.get(nodeId);
            }
            @Override
            public KAddress getSelfAdr() {
                return selfAdr;
            }

            @Override
            public TorrentDetails getTorrentDetails() {
                return torrentDetails;
            }

            @Override
            public void next(ComponentProxy proxy, GlobalView gv, KompicsEvent event) {
                if (state == 1 && event instanceof BasicContentMsg) {
                    BasicContentMsg<KAddress, KHeader<KAddress>, Object> msg = (BasicContentMsg) event;
                    if (msg.getHeader().getSource().getId().equals(selfAdr.getId())) {
                        LOG.info("upld state2 - outgoing traffic");
                        proxy.trigger(event, proxy.getNegative(Network.class).getPair());
                    } else if (msg.getHeader().getDestination().getId().equals(selfAdr.getId())) {
                        LOG.info("upld state2 - incoming traffic");
//                        proxy.trigger(event, proxy.getPositive(Network.class).getPair());
                        //delay it
                        scheduleNetworkDelay(proxy, msg, delayParam.getValue0(), delayParam.getValue1());
                        pendingMsg++;
                    } 
                    return;
                }
                if(state == 1 && event instanceof TorrentDriverComp.NetworkDelay) {
                    proxy.trigger(event, proxy.getPositive(Network.class).getPair());
                    pendingMsg--;
                }
                gv.terminate();
                throw new RuntimeException("state" + state + " illegal event:" + event.getClass().getCanonicalName());
            }
            
            private void scheduleNetworkDelay(ComponentProxy proxy, KContentMsg msg, int minDelay, int maxDelay) {
                long delay = minDelay + rand.nextInt(maxDelay - minDelay);
                ScheduleTimeout st = new ScheduleTimeout(delay);
                Timeout t = new NetworkDelay(st, msg);
                st.setTimeoutEvent(t);
                proxy.trigger(st, proxy.getNegative(Timer.class).getPair());
            }
        };
    }

    private static KAddress getNodeAdr(int nodeId) {
        try {
            return NatAwareAddressImpl.open(new BasicAddress(InetAddress.getByName("193.0.0." + nodeId), appPort, new IntIdentifier(nodeId)));
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static long getNodeSeed(int nodeId) {
        return scenarioSeed + nodeId;
    }
}
