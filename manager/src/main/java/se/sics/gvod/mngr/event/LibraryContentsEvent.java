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
package se.sics.gvod.mngr.event;

import java.util.List;
import java.util.Map;
import org.javatuples.Pair;
import se.sics.gvod.mngr.util.FileInfo;
import se.sics.gvod.mngr.util.LibraryElementSummary;
import se.sics.gvod.mngr.util.Result;
import se.sics.gvod.mngr.util.TorrentInfo;
import se.sics.kompics.Direct;
import se.sics.ktoolbox.util.identifiable.Identifier;
import se.sics.ktoolbox.util.identifiable.basic.UUIDIdentifier;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class LibraryContentsEvent {
    public static class Request extends Direct.Request<Response> implements VoDMngrEvent {
        public final Identifier eventId;
        
        public Request(Identifier eventId) {
            this.eventId = eventId;
        }
        
        public Request() {
            this(UUIDIdentifier.randomId());
        }
        
        public Response success(List<LibraryElementSummary> content) {
            return new Response(this, Result.success(), content);
        }
        
        @Override
        public Identifier getId() {
            return eventId;
        }
        
        @Override
        public String toString() {
            return "LibraryContentsRequest<" + getId() + ">";
        }
    }
    
    public static class Response implements Direct.Response, VoDMngrEvent {
        public final Request req;
        public final Result result;
        public final List<LibraryElementSummary> content;
        
        private Response(Request req, Result result, List<LibraryElementSummary> content) {
            this.req = req;
            this.result = result;
            this.content = content;
        }
        
        @Override
        public Identifier getId() {
            return req.getId();
        }
        
         @Override
        public String toString() {
            return "LibraryContentsResponse<" + getId() + ">";
        }
    }
}
