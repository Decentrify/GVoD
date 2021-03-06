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
package se.sics.nstream.hops.library.event.core;

import com.google.common.base.Optional;
import java.util.List;
import java.util.Map;
import se.sics.kompics.Direct;
import se.sics.kompics.Promise;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.ktoolbox.util.result.Result;
import se.sics.nstream.StreamEvent;
import se.sics.nstream.hops.kafka.KafkaEndpoint;
import se.sics.nstream.hops.kafka.KafkaResource;
import se.sics.nstream.hops.storage.hdfs.HDFSEndpoint;
import se.sics.nstream.hops.storage.hdfs.HDFSResource;
import se.sics.nstream.library.restart.LibTFSMEvent;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class HopsTorrentDownloadEvent {

  public static class StartRequest extends Promise<StartResponse> implements StreamEvent, LibTFSMEvent {

    public final Identifier eventId;
    public final OverlayId torrentId;
    public final String torrentName;
    public final Integer projectId;
    public final Integer datasetId;
    public final HDFSEndpoint hdfsEndpoint;
    public final HDFSResource manifest;
    public final List<KAddress> partners;

    public StartRequest(Identifier eventId, OverlayId torrentId, String torrentName, Integer projectId, 
      Integer datasetId, HDFSEndpoint hdfsEndpoint, HDFSResource manifest, List<KAddress> partners) {
      this.eventId = eventId;
      this.torrentId = torrentId;
      this.torrentName = torrentName;
      this.projectId = projectId;
      this.datasetId = datasetId;
      this.hdfsEndpoint = hdfsEndpoint;
      this.manifest = manifest;
      this.partners = partners;
    }

    public StartRequest(OverlayId torrentId, String torrentName, Integer projectId, Integer datasetId, 
      HDFSEndpoint hdfsEndpoint, HDFSResource manifest, List<KAddress> partners) {
      this(BasicIdentifiers.eventId(), torrentId, torrentName, projectId, datasetId, hdfsEndpoint, manifest, partners);
    }

    @Override
    public Identifier getId() {
      return eventId;
    }

    @Override
    public StartResponse fail(Result result) {
      return new StartFailed(this, result);
    }

    @Override
    public StartResponse success(Result result) {
      return new StartSuccess(this, result);
    }

    @Override
    public Identifier getLibTFSMId() {
      return torrentId.baseId;
    }
  }

  public static interface StartResponse extends Direct.Response, StreamEvent {
  }

  public static class StartSuccess implements StartResponse {

    public final StartRequest req;
    public final Result<Boolean> result;

    public StartSuccess(StartRequest req, Result<Boolean> result) {
      this.req = req;
      this.result = result;
    }

    @Override
    public Identifier getId() {
      return req.eventId;
    }
  }

  public static class StartFailed implements StartResponse {

    public final StartRequest req;
    public final Result result;

    public StartFailed(StartRequest req, Result result) {
      this.req = req;
      this.result = result;
    }

    @Override
    public Identifier getId() {
      return req.eventId;
    }
  }

  public static class TorrentResult implements StartResponse {

    public final StartRequest req;
    public final Result<Boolean> manifest;

    TorrentResult(StartRequest req, Result<Boolean> manifest) {
      this.req = req;
      this.manifest = manifest;
    }

    @Override
    public Identifier getId() {
      return req.getId();
    }
  }

  public static class AdvanceRequest extends Promise<AdvanceResponse> implements StreamEvent, LibTFSMEvent {

    public final Identifier eventId;
    public final OverlayId torrentId;
    public final Result<Boolean> result;
    public final HDFSEndpoint hdfsEndpoint;
    public final Optional<KafkaEndpoint> kafkaEndpoint;
    public final Map<String, HDFSResource> hdfsDetails;
    public final Map<String, KafkaResource> kafkaDetails;

    public AdvanceRequest(Identifier eventId, OverlayId torrentId, HDFSEndpoint hdfsEndpoint,
      Optional<KafkaEndpoint> kafkaEndpoint,
      Map<String, HDFSResource> hdfsDetails, Map<String, KafkaResource> kafkaDetails) {
      this.eventId = eventId;
      this.torrentId = torrentId;
      this.result = Result.success(true);
      this.hdfsEndpoint = hdfsEndpoint;
      this.kafkaEndpoint = kafkaEndpoint;
      this.hdfsDetails = hdfsDetails;
      this.kafkaDetails = kafkaDetails;
    }

    public AdvanceRequest(OverlayId torrentId, HDFSEndpoint hdfsEndpoint, Optional<KafkaEndpoint> kafkaEndpoint,
      Map<String, HDFSResource> hdfsDetails, Map<String, KafkaResource> kafkaDetails) {
      this(BasicIdentifiers.eventId(), torrentId, hdfsEndpoint, kafkaEndpoint, hdfsDetails, kafkaDetails);
    }

    @Override
    public Identifier getId() {
      return eventId;
    }

    @Override
    public AdvanceResponse success(Result result) {
      return new AdvanceResponse(this, result);
    }

    @Override
    public AdvanceResponse fail(Result result) {
      return new AdvanceResponse(this, result);
    }

    @Override
    public Identifier getLibTFSMId() {
      return torrentId.baseId;
    }
  }

  public static class AdvanceResponse implements Direct.Response, StreamEvent {

    public final AdvanceRequest req;
    public final Result<Boolean> result;

    public AdvanceResponse(AdvanceRequest req, Result<Boolean> result) {
      this.req = req;
      this.result = result;
    }

    @Override
    public Identifier getId() {
      return req.eventId;
    }
  }
}
