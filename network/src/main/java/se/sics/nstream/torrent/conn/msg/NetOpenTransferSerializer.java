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
package se.sics.nstream.torrent.conn.msg;

import com.google.common.base.Optional;
import io.netty.buffer.ByteBuf;
import se.sics.kompics.util.Identifier;
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistry;
import se.sics.nstream.FileId;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NetOpenTransferSerializer {
    public static class DefinitionRequest implements Serializer {
        private final int id;
        private final Class msgIdType;
        
        public DefinitionRequest(int id) {
            this.id = id;
            this.msgIdType = IdentifierRegistry.lookup(BasicIdentifiers.Values.MSG.toString()).idType();
        }
        
        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            NetOpenTransfer.Request obj = (NetOpenTransfer.Request)o;
            Serializers.lookupSerializer(msgIdType).toBinary(obj.msgId, buf);
            Serializers.lookupSerializer(FileId.class).toBinary(obj.fileId, buf);
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Identifier msgId = (Identifier)Serializers.lookupSerializer(msgIdType).fromBinary(buf, hint);
            FileId fileId = (FileId)Serializers.lookupSerializer(FileId.class).fromBinary(buf, hint);
            return new NetOpenTransfer.Request(msgId, fileId);
        }
    }
    
     public static class DefinitionResponse implements Serializer {
        private final int id;
        private final Class msgIdType;
        
        public DefinitionResponse(int id) {
            this.id = id;
            this.msgIdType = IdentifierRegistry.lookup(BasicIdentifiers.Values.MSG.toString()).idType();
        }
        
        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            NetOpenTransfer.Response obj = (NetOpenTransfer.Response)o;
            Serializers.lookupSerializer(msgIdType).toBinary(obj.msgId, buf);
            Serializers.lookupSerializer(FileId.class).toBinary(obj.fileId, buf);
            buf.writeBoolean(obj.result);
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Identifier msgId = (Identifier)Serializers.lookupSerializer(msgIdType).fromBinary(buf, hint);
            FileId fileId = (FileId)Serializers.lookupSerializer(FileId.class).fromBinary(buf, hint);
            boolean result = buf.readBoolean();
            return new NetOpenTransfer.Response(msgId, fileId, result);
        }
    }
}
