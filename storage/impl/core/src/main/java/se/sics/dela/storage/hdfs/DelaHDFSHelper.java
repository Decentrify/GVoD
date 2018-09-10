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
package se.sics.dela.storage.hdfs;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.ktoolbox.util.result.Result;
import se.sics.nstream.hops.manifest.ManifestHelper;
import se.sics.nstream.hops.manifest.ManifestJSON;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class DelaHDFSHelper {

  private final static Logger LOG = LoggerFactory.getLogger(DelaHDFSHelper.class);
  private static String logPrefix = "";
  
  public static Result<ManifestJSON> readManifest(UserGroupInformation ugi, final HDFSEndpoint hdfsEndpoint,
    HDFSResource hdfsResource) {
    final String filePath = hdfsResource.dirPath + Path.SEPARATOR + hdfsResource.fileName;
    LOG.debug("{}reading manifest:{}", new Object[]{logPrefix, filePath});
    try {
      Result<ManifestJSON> result = ugi.doAs(new PrivilegedExceptionAction<Result<ManifestJSON>>() {
        @Override
        public Result<ManifestJSON> run() {
          try (DistributedFileSystem fs = (DistributedFileSystem) FileSystem.get(hdfsEndpoint.hdfsConfig)) {
            if (!fs.isFile(new Path(filePath))) {
              LOG.warn("{}file does not exist", new Object[]{logPrefix, filePath});
              return Result.externalSafeFailure(new HDFSException("hdfs file read"));
            }
            try (FSDataInputStream in = fs.open(new Path(filePath))) {
              long manifestLength = fs.getLength(new Path(filePath));
              byte[] manifestByte = new byte[(int) manifestLength];
              in.readFully(manifestByte);
              ManifestJSON manifest = ManifestHelper.getManifestJSON(manifestByte);
              return Result.success(manifest);
            }
          } catch (IOException ex) {
            LOG.warn("{}could not read file:{} ex:{}", new Object[]{logPrefix, filePath, ex.getMessage()});
            return Result.externalSafeFailure(new HDFSException("hdfs file read", ex));
          }
        }
      });
      LOG.trace("{}op completed", new Object[]{logPrefix});
      return result;
    } catch (IOException | InterruptedException ex) {
      LOG.error("{}unexpected exception:{}", logPrefix, ex);
      return Result.externalSafeFailure(new HDFSException("hdfs file read", ex));
    }
  }

  public static Result<Boolean> writeManifest(UserGroupInformation ugi, final HDFSEndpoint hdfsEndpoint,
    final HDFSResource hdfsResource, final ManifestJSON manifest) {
    final String filePath = hdfsResource.dirPath + Path.SEPARATOR + hdfsResource.fileName;
    LOG.debug("{}writing manifest:{}", new Object[]{logPrefix, filePath});
    try {
      Result<Boolean> result = ugi.doAs(new PrivilegedExceptionAction<Result<Boolean>>() {
        @Override
        public Result<Boolean> run() {
          try (FileSystem fs = FileSystem.get(hdfsEndpoint.hdfsConfig)) {
            if (!fs.isDirectory(new Path(hdfsResource.dirPath))) {
              fs.mkdirs(new Path(hdfsResource.dirPath));
            }
            if (fs.isFile(new Path(filePath))) {
              return Result.success(false);
            }
            try (FSDataOutputStream out = fs.create(new Path(filePath))) {
              byte[] manifestByte = ManifestHelper.getManifestByte(manifest);
              out.write(manifestByte);
              out.flush();
              return Result.success(true);
            }
          } catch (IOException ex) {
            LOG.warn("{}could not create file:{}", logPrefix, ex.getMessage());
            return Result.externalUnsafeFailure(new HDFSException("hdfs file createWithLength", ex));
          }
        }
      });
      LOG.trace("{}op completed", new Object[]{logPrefix});
      return result;
    } catch (IOException | InterruptedException ex) {
      LOG.error("{}unexpected exception:{}", logPrefix, ex);
      return Result.externalUnsafeFailure(new HDFSException("hdfs file createWithLength", ex));
    }
  }
}
