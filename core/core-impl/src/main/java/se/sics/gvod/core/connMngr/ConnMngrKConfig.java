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
package se.sics.gvod.core.connMngr;

import se.sics.ktoolbox.util.aggregation.AggregationLevelOption;
import se.sics.ktoolbox.util.config.KConfigOption;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ConnMngrKConfig {

    public final static KConfigOption.Basic<Integer> defaultMaxPipeline = new KConfigOption.Basic("vod.connection.maxPipeline", Integer.class);
    public final static KConfigOption.Basic<Integer> piecesPerBlock = new KConfigOption.Basic("vod.video.piecesPerBlock", Integer.class);
    public final static KConfigOption.Basic<Long> updatePeriod = new KConfigOption.Basic("vod.connection.updatePeriod", Long.class);
    public final static KConfigOption.Basic<Long> reqTimeoutPeriod = new KConfigOption.Basic("vod.connection.reqTimeoutPeriod", Long.class);
    public final static AggregationLevelOption aggLevel = new AggregationLevelOption("vod.connection.aggLevel");
    public final static KConfigOption.Basic<Long> aggPeriod = new KConfigOption.Basic("vod.connection.aggPeriod", Long.class);
}
