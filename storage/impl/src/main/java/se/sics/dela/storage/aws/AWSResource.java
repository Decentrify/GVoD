/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * KompicsToolbox is free software; you can redistribute it and/or
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
package se.sics.dela.storage.aws;

import se.sics.dela.storage.StorageResource;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class AWSResource implements StorageResource {

  //bucket
  public final String bucket;
  //key
  public final String libDir;
  public final String relativePath;
  public final String file;

  public AWSResource(String bucket, String libDir, String relativePath, String file) {
    this.bucket = bucket;
    this.libDir = libDir;
    this.relativePath = relativePath;
    this.file = file;
  }

  private String fullPath() {
    return bucket + "/" + blobPath();
  }

  private String blobPath() {
    String p1 = stripEdgeSeparators(libDir);
    String p2 = stripEdgeSeparators(relativePath);
    if (p1.equals("")) {
      return p2 + "/" + file;
    } else {
      return p1 + "/" + p2 + "/" + file;
    }
  }

  private String stripEdgeSeparators(String path) {
    String p = path;
    if (p.equals("/")) {
      p = "";
    } else {
      if (p.endsWith("/")) {
        p = p.substring(0, p.length() - 1);
      }
      if (p.startsWith("/")) {
        p = p.substring(1, p.length());
      }
    }
    return p;
  }

  @Override
  public String getSinkName() {
    return "aws:/" + fullPath();
  }

  public String getKey() {
    return blobPath();
  }
  
  public String getBlobName() {
    return fullPath();
  }

  public AWSResource withFile(String file) {
    return new AWSResource(bucket, libDir, relativePath, file);
  }
}