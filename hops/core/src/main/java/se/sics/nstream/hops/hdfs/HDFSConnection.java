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
package se.sics.nstream.hops.hdfs;

import org.apache.hadoop.conf.Configuration;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class HDFSConnection {

    public final Configuration hdfsConfig;
    
    public HDFSConnection(String hopsIp, int hopsPort) {
        this.hdfsConfig = new Configuration();
        String hopsURL = "hdfs://" + hopsIp + ":" + hopsPort;
        hdfsConfig.set("fs.defaultFS", hopsURL);
    }
    
    public HDFSConnection(String hdfsXMLPath) {
        this.hdfsConfig = new Configuration();
        this.hdfsConfig.addResource(hdfsXMLPath);
    }
}