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
package se.sics.gvod.core.event;

import java.util.Map;
import org.javatuples.Pair;
import se.sics.gvod.common.event.GVoDEvent;
import se.sics.gvod.core.util.FileStatus;
import se.sics.gvod.core.util.ResponseStatus;
import se.sics.kompics.id.Identifier;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class GetLibrary {
    public static class Request implements GVoDEvent {
        public final Identifier id;
        
        public Request() {
            this.id = BasicIdentifiers.eventId();
        }

        @Override
        public Identifier getId() {
            return id;
        }
        
        public Indication answer(ResponseStatus respStatus, Map<String, Pair<FileStatus, Identifier>> fileStatusMap) {
            return new Indication(id, respStatus, fileStatusMap);
        }
    }
    
    public static class Indication implements GVoDEvent {
        public final Identifier id;
        public final ResponseStatus respStatus;
        public final Map<String, Pair<FileStatus, Identifier>> fileStatusMap;
        
        public Indication(Identifier id, ResponseStatus respStatus, Map<String, Pair<FileStatus, Identifier>> fileStatusMap) {
            this.id = id;
            this.respStatus = respStatus;
            this.fileStatusMap = fileStatusMap;
        }
        
        public Indication(ResponseStatus respStatus, Map<String, Pair<FileStatus, Identifier>> fileStatusMap) {
            this(BasicIdentifiers.eventId(), respStatus, fileStatusMap);
        }

        @Override
        public Identifier getId() {
            return id;
        }
    }
}
