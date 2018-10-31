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
 * along with this program; if not, append to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.dela.storage.disk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Consumer;
import org.javatuples.Pair;
import org.slf4j.Logger;
import se.sics.dela.storage.StorageEndpoint;
import se.sics.dela.storage.StorageResource;
import se.sics.dela.storage.common.DelaReadStream;
import se.sics.dela.storage.common.DelaStorageException;
import se.sics.dela.util.TimerProxy;
import se.sics.ktoolbox.util.trysf.Try;
import se.sics.ktoolbox.util.trysf.TryHelper;
import se.sics.nstream.util.range.KRange;
import se.sics.dela.storage.common.DelaAppendStream;
import se.sics.dela.storage.core.DelaStorageComp;
import se.sics.kompics.util.Identifier;
import se.sics.dela.storage.common.DelaFileHandler;
import se.sics.dela.storage.common.DelaHelper;
import se.sics.dela.storage.common.DelaStorageHandler;
import se.sics.dela.storage.common.StorageType;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class DelaDisk {

  public static class StorageCompProvider implements se.sics.dela.storage.mngr.StorageProvider<DelaStorageComp> {

    public final Identifier self;
    public final DiskEndpoint endpoint = new DiskEndpoint();

    public StorageCompProvider(Identifier self) {
      this.self = self;
    }

    @Override
    public Pair<DelaStorageComp.Init, Long> initiate(StorageResource resource, Logger logger) {
      DiskResource diskResource = (DiskResource) resource;
      DelaFileHandler storage = new FileHandler(endpoint, diskResource);
      Try<Long> pos = DelaDisk.fileSize(endpoint, diskResource);
      if (pos.isFailure()) {
        throw new RuntimeException(TryHelper.tryError(pos));
      }
      DelaStorageComp.Init init = new DelaStorageComp.Init(storage, pos.get());
      return Pair.with(init, pos.get());
    }

    @Override
    public String getName() {
      return endpoint.getEndpointName();
    }

    @Override
    public Class<DelaStorageComp> getStorageDefinition() {
      return DelaStorageComp.class;
    }

    @Override
    public StorageEndpoint getEndpoint() {
      return endpoint;
    }
  }

  public static class StorageHandler implements DelaStorageHandler<DiskEndpoint, DiskResource> {

    public final DiskEndpoint endpoint;

    public StorageHandler(DiskEndpoint endpoint) {
      this.endpoint = endpoint;
    }

    @Override
    public Try<DelaFileHandler<DiskEndpoint, DiskResource>> get(DiskResource resource) {
      File f = new File(resource.dirPath, resource.fileName);
      if (!f.exists()) {
        return new Try.Failure(new DelaStorageException(DelaStorageException.RESOURCE_DOES_NOT_EXIST, StorageType.DISK));
      }
      DelaFileHandler<DiskEndpoint, DiskResource> file = new FileHandler(endpoint, resource);
      return new Try.Success(file);
    }

    @Override
    public Try<DelaFileHandler<DiskEndpoint, DiskResource>> create(DiskResource resource) {
      File dir = new File(resource.dirPath);
      if (dir.isFile()) {
        String msg = "resource parent dir:" + dir.getAbsolutePath() + " is a file";
        return new Try.Failure(new DelaStorageException(msg, StorageType.DISK));
      }
      if (!dir.exists()) {
        dir.mkdirs();
      }
      File f = new File(dir, resource.fileName);
      try {
        f.createNewFile();
        DelaFileHandler<DiskEndpoint, DiskResource> file = new FileHandler(endpoint, resource);
        return new Try.Success(file);
      } catch (IOException ex) {
        String msg = "could not create file:" + f.getAbsolutePath();
        return new Try.Failure(new DelaStorageException(msg, StorageType.DISK));
      }
    }

    @Override
    public Try<Boolean> delete(DiskResource resource) {
      File f = new File(resource.dirPath, resource.fileName);
      return new Try.Success(f.delete());
    }
  }

  public static class FileHandler implements DelaFileHandler<DiskEndpoint, DiskResource> {

    public final DiskEndpoint endpoint;
    public final DiskResource resource;

    public FileHandler(DiskEndpoint endpoint, DiskResource resource) {
      this.endpoint = endpoint;
      this.resource = resource;
    }

    @Override
    public DiskEndpoint getEndpoint() {
      return endpoint;
    }

    @Override
    public DiskResource getResource() {
      return resource;
    }
    
    @Override
    public StorageType storageType() {
      return StorageType.DISK;
    }

    @Override
    public Try<Long> size() {
      return DelaDisk.fileSize(endpoint, resource);
    }

    @Override
    public Try<byte[]> read(KRange range) {
      int readLength = (int) (range.upperAbsEndpoint() - range.lowerAbsEndpoint() + 1);
      byte[] readVal = new byte[readLength];
      int readPos = (int) range.lowerAbsEndpoint();
      String f = resource.dirPath + File.separator + resource.fileName;
      try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
        raf.seek(readPos);
        raf.readFully(readVal);
        return new Try.Success(readVal);
      } catch (IOException ex) {
        String msg = "could not read file:" + f;
        return new Try.Failure(new DelaStorageException(msg, ex, StorageType.DISK));
      }
    }

    @Override
    public Try<byte[]> readAll() {
      Try<byte[]> result = new Try.Success(true)
        .flatMap(TryHelper.tryFSucc0(() -> size())) //get file size
        .flatMap(DelaHelper.fullRange(StorageType.DISK, resource)) //build range
        .flatMap(TryHelper.tryFSucc1((KRange range) -> read(range))); //read all range
      return result;
    }

    @Override
    public Try<Boolean> append(long pos, byte[] data) {
      String f = resource.dirPath + File.separator + resource.fileName;
      try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
        raf.seek(pos);
        raf.write(data);
        return new Try.Success(true);
      } catch (IOException ex) {
        String msg = "could not write file:" + f;
        return new Try.Failure(new DelaStorageException(msg, ex, StorageType.DISK));
      }
    }

    @Override
    public Try<DelaReadStream> readSession(TimerProxy timer) {
      String filePath = resource.dirPath + File.separator + resource.fileName;
      try {
        RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
        return new Try.Success(new ReadStream(filePath, raf));
      } catch (FileNotFoundException ex) {
        String msg = "could not find file:" + filePath;
        return new Try.Failure(new DelaStorageException(msg, ex, StorageType.DISK));
      }
    }

    @Override
    public Try<DelaAppendStream> appendSession(TimerProxy timer) {
      String filePath = resource.dirPath + File.separator + resource.fileName;
      try {
        RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
        return new Try.Success(new AppendStream(filePath, raf));
      } catch (FileNotFoundException ex) {
        String msg = "could not find file:" + filePath;
        return new Try.Failure(new DelaStorageException(msg, ex, StorageType.DISK));
      }
    }
  }

  public static class ReadStream implements DelaReadStream {

    private final String filePath;
    private final RandomAccessFile raf;

    public ReadStream(String filePath, RandomAccessFile raf) {
      this.filePath = filePath;
      this.raf = raf;
    }

    @Override
    public void read(KRange range, Consumer<Try<byte[]>> callback) {
      int readLength = (int) (range.upperAbsEndpoint() - range.lowerAbsEndpoint() + 1);
      byte[] readVal = new byte[readLength];
      int readPos = (int) range.lowerAbsEndpoint();
      try {
        raf.seek(readPos);
        raf.readFully(readVal);
        callback.accept(new Try.Success(readVal));
      } catch (IOException ex) {
        String msg = "could not read file:" + filePath;
        callback.accept(new Try.Failure(new DelaStorageException(msg, ex, StorageType.DISK)));
      }
    }

    @Override
    public Try<Boolean> close() {
      try {
        raf.close();
        return new Try.Success(true);
      } catch (IOException ex) {
        String msg = "closing file:" + filePath;
        return new Try.Failure(new DelaStorageException(msg, ex, StorageType.DISK));
      }
    }
  }

  public static class AppendStream implements DelaAppendStream {

    private final String filePath;
    private final RandomAccessFile raf;

    public AppendStream(String filePath, RandomAccessFile raf) {
      this.filePath = filePath;
      this.raf = raf;
    }

    @Override
    public void write(long pos, byte[] data, Consumer<Try<Boolean>> callback) {
      try {
        raf.seek(pos);
        raf.write(data);
        callback.accept(new Try.Success(true));
      } catch (IOException ex) {
        String msg = "could not write file:" + filePath;
        callback.accept(new Try.Failure(new DelaStorageException(msg, ex, StorageType.DISK)));
      }
    }

    @Override
    public Try<Boolean> close() {
      try {
        raf.close();
        return new Try.Success(true);
      } catch (IOException ex) {
        String msg = "closing file:" + filePath;
        return new Try.Failure(new DelaStorageException(msg, ex, StorageType.DISK));
      }
    }
  }

  public static Try<Long> fileSize(DiskEndpoint endpoint, DiskResource resource) {
    File f = new File(resource.dirPath, resource.fileName);
    return new Try.Success(f.length());
  }
}
