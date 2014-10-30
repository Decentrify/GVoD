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
package se.sics.gvod.network.filters;

import org.javatuples.Pair;
import se.sics.gvod.net.msgs.DirectMsg;
import se.sics.gvod.network.nettymsg.MyNetMsg;
import se.sics.gvod.network.tags.ContextTag;
import se.sics.gvod.network.tags.OverlayTag;
import se.sics.gvod.network.tags.TagType;
import se.sics.kompics.ChannelFilter;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 * and filter with context and overlayId
 */
public class ContextOverlayFilter extends ChannelFilter<DirectMsg, Pair<ContextTag, OverlayTag>> {

    public ContextOverlayFilter(ContextTag contextTag, OverlayTag overlayTag) {
        super(DirectMsg.class, Pair.with(contextTag, overlayTag), true);
    }

    @Override
    public Pair<ContextTag, OverlayTag> getValue(DirectMsg msg) {
        if (msg instanceof MyNetMsg.Request) {
            MyNetMsg.Request req = (MyNetMsg.Request) msg;
            return Pair.with((ContextTag)req.tags.get(TagType.CONTEXT), (OverlayTag)req.tags.get(TagType.OVERLAY));
        } else if (msg instanceof MyNetMsg.Response) {
            MyNetMsg.Response resp = (MyNetMsg.Response) msg;
            return Pair.with((ContextTag)resp.tags.get(TagType.CONTEXT), (OverlayTag)resp.tags.get(TagType.OVERLAY));
        } else if (msg instanceof MyNetMsg.OneWay) {
            MyNetMsg.OneWay oneWay =  (MyNetMsg.OneWay) msg;
            return Pair.with((ContextTag)oneWay.tags.get(TagType.CONTEXT), (OverlayTag)oneWay.tags.get(TagType.OVERLAY));
        } else {
            return Pair.with(null, null);
        }
    }
}
