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
 * GNU General Public License for more defLastBlock.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.nstream.library.disk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import se.sics.kompics.config.Config;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayIdFactory;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.ktoolbox.util.result.Result;
import se.sics.nstream.hops.library.LibraryCtrl;
import se.sics.nstream.hops.library.Torrent;
import se.sics.nstream.hops.library.util.LibrarySummaryHelper;
import se.sics.nstream.hops.library.util.LibrarySummaryJSON;
import se.sics.nstream.library.util.TorrentState;
import se.sics.nstream.storage.durable.util.MyStream;
import se.sics.nstream.util.TorrentExtendedStatus;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class DiskLibrary implements LibraryCtrl {

  private final OverlayIdFactory torrentIdFactory;
  private final Config config;
  private final DiskLibraryConfig diskLibraryConfig;

  private Map<OverlayId, Torrent> torrents = new HashMap<>();

  public DiskLibrary(OverlayIdFactory torrentIdFactory, Config config) {
    this.torrentIdFactory = torrentIdFactory;
    this.config = config;
    this.diskLibraryConfig = new DiskLibraryConfig(config);
  }

  @Override
  public Map<OverlayId, Torrent> start() {
    return readTorrents(config);
  }

  @Override
  public void stop() {
  }

  private Map<OverlayId, Torrent> readTorrents(Config config) {
    Result<LibrarySummaryJSON> librarySummary = LibrarySummaryHelper.readTorrentList(diskLibraryConfig.librarySummary);
    if (!librarySummary.isSuccess()) {
      throw new RuntimeException("TODO fix me - corrupted library");
    }
    return LibrarySummaryHelper.fromSummary(librarySummary.getValue(), torrentIdFactory, config);
  }

  @Override
  public Map<OverlayId, Torrent> getTorrents() {
    return torrents;
  }

  @Override
  public boolean containsTorrent(OverlayId tId) {
    return torrents.containsKey(tId);
  }

  @Override
  public TorrentState stateOf(OverlayId tId) {
    Torrent t = torrents.get(tId);
    if (t == null) {
      return TorrentState.NONE;
    }
    return t.getTorrentStatus();
  }

  @Override
  public void killing(OverlayId torrentId) {
    torrents.get(torrentId).setTorrentStatus(TorrentState.KILLING);
  }

  @Override
  public void killed(OverlayId torrentId) {
    Torrent torrent = torrents.remove(torrentId);
    if (torrent != null) {
      updateSummary();
    }
  }

  @Override
  public void prepareUpload(OverlayId torrentId, Integer projectId, Integer datasetId, String torrentName) {
    TorrentExtendedStatus extendedStatus = new TorrentExtendedStatus(torrentId, TorrentState.PREPARE_UPLOAD, 0, 100);
    Torrent torrent = new Torrent(projectId, datasetId, torrentName, extendedStatus);
    torrents.put(torrentId, torrent);
  }

  @Override
  public void upload(OverlayId torrentId, MyStream manifestStream) {
    Torrent torrent = torrents.get(torrentId);
    torrent.setTorrentStatus(TorrentState.UPLOADING);
    torrent.setManifestStream(manifestStream);
    updateSummary();
  }

  @Override
  public void prepareDownload(OverlayId torrentId, Integer projectId, Integer datasetId, String torrentName,
    List<KAddress> partners) {
    TorrentExtendedStatus extendedStatus = new TorrentExtendedStatus(torrentId, TorrentState.PREPARE_DOWNLOAD, 0, 0);
    Torrent torrent = new Torrent(projectId, datasetId, torrentName, extendedStatus);
    torrent.setPartners(partners);
    torrents.put(torrentId, torrent);
  }

  @Override
  public void download(OverlayId torrentId, MyStream manifestStream) {
    Torrent torrent = torrents.get(torrentId);
    torrent.setTorrentStatus(TorrentState.DOWNLOADING);
    torrent.setManifestStream(manifestStream);
    updateSummary();
  }

  @Override
  public void finishDownload(OverlayId torrentId) {
    Torrent torrent = torrents.get(torrentId);
    torrent.setTorrentStatus(TorrentState.UPLOADING);
    updateSummary();
  }

  private void updateSummary() {
    LibrarySummaryJSON summaryResult = LibrarySummaryHelper.toSummary(torrents);
    Result<Boolean> writeResult 
      = LibrarySummaryHelper.writeTorrentList(diskLibraryConfig.librarySummary, summaryResult);
    if (!writeResult.isSuccess()) {
      //TODO - try again next time?
    }
  }

  @Override
  public void updateDownload(OverlayId torrentId, long speed, double dynamic) {
    Torrent torrent = torrents.get(torrentId);
    torrent.getStatus().setDownloadSpeed(speed);
    torrent.getStatus().setPercentageComplete(dynamic);
  }
}
