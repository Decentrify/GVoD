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
package se.sics.silk.r2torrent.torrent.state;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import se.sics.kompics.util.Identifier;
import se.sics.silk.r2torrent.transfer.events.R1TransferLeecherEvents;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class R1FileUploadLeechersState {

  Map<Identifier, R1FileUploadLeecherState> pendingLeechers = new HashMap<>();
  public final Map<Identifier, R1FileUploadLeecherState> connectedLeechers = new HashMap<>();

  public R1FileUploadLeechersState() {
  }

  public void pendingLeecher(R1TransferLeecherEvents.ConnectReq req) {
    pendingLeechers.put(req.leecherAdr.getId(), new R1FileUploadLeecherState(req));
  }

  public void pendingConnected(Consumer<R1TransferLeecherEvents.ConnectReq> sendEvent) {
    pendingLeechers.values().stream().forEach((fls) -> {
      sendEvent.accept(fls.req);
      connectedLeechers.put(fls.leecher.getId(), fls);
    });
    pendingLeechers.clear();
  }
  
  public void connected(R1TransferLeecherEvents.ConnectReq req, 
    Consumer<R1TransferLeecherEvents.ConnectReq> sendEvent) {
    sendEvent.accept(req);
    connectedLeechers.put(req.leecherAdr.getId(), new R1FileUploadLeecherState(req));
  }

  public void disconnected1(R1TransferLeecherEvents.Disconnected req) {
    pendingLeechers.remove(req.nodeId);
  }
  
  public void disconnected2(R1TransferLeecherEvents.Disconnected req) {
    connectedLeechers.remove(req.nodeId);
  }
  
  public boolean empty() {
    return pendingLeechers.isEmpty() && connectedLeechers.isEmpty();
  }
}