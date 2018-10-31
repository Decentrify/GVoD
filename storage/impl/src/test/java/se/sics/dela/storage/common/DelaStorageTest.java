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
 * along with this program; if not, append to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.dela.storage.common;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Assert;
import org.junit.Test;
import se.sics.dela.storage.StorageResource;
import se.sics.dela.storage.disk.DelaDisk;
import se.sics.dela.storage.disk.DiskEndpoint;
import se.sics.dela.storage.disk.DiskResource;
import se.sics.dela.storage.hdfs.HDFSEndpoint;
import se.sics.dela.storage.hdfs.DelaHDFS;
import se.sics.dela.storage.hdfs.HDFSResource;
import se.sics.dela.util.TimerProxy;
import se.sics.kompics.ComponentProxy;
import se.sics.ktoolbox.util.trysf.Try;
import se.sics.ktoolbox.util.trysf.TryHelper;
import static se.sics.ktoolbox.util.trysf.TryHelper.tryAssert;
import se.sics.nstream.util.range.KBlockImpl;
import se.sics.nstream.util.range.KRange;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class DelaStorageTest {

  //************************************************HDFS****************************************************************
//  @Test
  public void testHDFSAppend() throws IOException, Throwable {
    HDFSEndpoint endpoint = HDFSEndpoint.getBasic("vagrant", "10.0.2.15", 8020).get();
    HDFSResource resource = new HDFSResource("/test", "file");
    Try<DelaHDFS.StorageHandler> storage = DelaHDFS.StorageHandler.handler(endpoint);
    testAppend(storage.checkedGet(), resource);
  }

//    @Test
  public void testHDFSMultiAppend() throws IOException, Throwable {
    HDFSEndpoint endpoint = HDFSEndpoint.getBasic("vagrant", "10.0.2.15", 8020).get();
    HDFSResource resource = new HDFSResource("/test", "file");
    Try<DelaHDFS.StorageHandler> storage = DelaHDFS.StorageHandler.handler(endpoint);
    testMultiAppend(storage.checkedGet(), resource);
  }

//    @Test
  public void testHDFSMultiRead() throws IOException, Throwable {
    HDFSEndpoint endpoint = HDFSEndpoint.getBasic("vagrant", "10.0.2.15", 8020).get();
    HDFSResource resource = new HDFSResource("/test", "file");
    Try<DelaHDFS.StorageHandler> storage = DelaHDFS.StorageHandler.handler(endpoint);
    testMultiRead(storage.checkedGet(), resource);
  }
  
//    @Test
  public void testHDFSRead() throws IOException, Throwable {
    HDFSEndpoint endpoint = HDFSEndpoint.getBasic("vagrant", "10.0.2.15", 8020).get();
    HDFSResource resource = new HDFSResource("/test", "file");
    Try<DelaHDFS.StorageHandler> storage = DelaHDFS.StorageHandler.handler(endpoint);
    testRead(storage.checkedGet(), resource);
  }
  
  //***************************************************DISK*************************************************************
//  @Test
  public void testDiskAppend() throws Throwable {
    DiskEndpoint endpoint = new DiskEndpoint();
    DiskResource resource = new DiskResource("src/test/resources", "disk_test_file");
    DelaDisk.StorageHandler storage = new DelaDisk.StorageHandler(endpoint);
    testAppend(storage, resource);
  }
  
//  @Test
  public void testDiskMultiAppend() throws Throwable {
    DiskEndpoint endpoint = new DiskEndpoint();
    DiskResource resource = new DiskResource("src/test/resources", "disk_test_file");
    DelaDisk.StorageHandler storage = new DelaDisk.StorageHandler(endpoint);
    testMultiAppend(storage, resource);
  }
  
//  @Test
  public void testDiskRead() throws Throwable {
    DiskEndpoint endpoint = new DiskEndpoint();
    DiskResource resource = new DiskResource("src/test/resources", "disk_test_file");
    DelaDisk.StorageHandler storage = new DelaDisk.StorageHandler(endpoint);
    testRead(storage, resource);
  }
  
//  @Test
  public void testDiskMultiRead() throws Throwable {
    DiskEndpoint endpoint = new DiskEndpoint();
    DiskResource resource = new DiskResource("src/test/resources", "disk_test_file");
    DelaDisk.StorageHandler storage = new DelaDisk.StorageHandler(endpoint);
    testMultiRead(storage, resource);
  }
  
  private DelaFileHandler prepareFile(DelaStorageHandler storage, StorageResource resource) throws Throwable {
    Try<DelaFileHandler> file = new Try.Success(true)
      .flatMap(TryHelper.tryFSucc0(() -> storage.delete(resource)))
      .flatMap(TryHelper.tryFSucc0(() -> storage.create(resource)));
    return file.checkedGet();
  }
  
  private void testAppend(DelaStorageHandler storage, StorageResource resource) throws Throwable {
    System.err.println("test write");
    DelaFileHandler file = prepareFile(storage, resource);
    Try<Boolean> result = new Try.Success(true)
      .flatMap(TryHelper.tryFSucc0(appendFile(file)))
      .flatMap(TryHelper.tryFSucc0(() -> file.size()))
      .map(tryAssert((Long size) -> Assert.assertEquals(10 * 10 * 1024 * 1024l, (long) size)))
      .flatMap(TryHelper.tryFSucc0(() -> storage.delete(resource)));
    result.checkedGet();
  }

  private Supplier<Try<Boolean>> appendFile(DelaFileHandler storage) {
    return () -> {
      int blockSize = 10 * 1024 * 1024;
      byte[] block = new byte[blockSize];
      Random rand = new Random(123);
      rand.nextBytes(block);

      long start = System.nanoTime();
      for (int i = 0; i < 10; i++) {
        storage.append(i * blockSize, block);
      }
      long end = System.nanoTime();
      long sizeMB = 10 * 10;
      double time = (double) (end - start) / (1000 * 1000 * 1000);
      System.err.println("write time(s):" + time);
      System.err.println("write avg speed(MB/s):" + sizeMB / time);
      return new Try.Success(true);
    };
  }

  private void testMultiAppend(DelaStorageHandler storage, StorageResource resource) throws Throwable {
    System.err.println("test multi write");
    DelaFileHandler file = prepareFile(storage, resource);
    Try<Boolean> result = new Try.Success(true)
      .flatMap(TryHelper.tryFSucc0(multiAppendFile(file)))
      .flatMap(TryHelper.tryFSucc0(() -> file.size()))
      .map(tryAssert((Long size) -> Assert.assertEquals(10 * 100 * 1024 * 1024l, (long) size)))
      .flatMap(TryHelper.tryFSucc0(() -> storage.delete(resource)));
    result.checkedGet();
  }

  private Supplier<Try<Boolean>> multiAppendFile(DelaFileHandler storage) {
    return () -> {
      int blockSize = 10 * 1024 * 1024;
      byte[] block = new byte[blockSize];
      Random rand = new Random(123);
      rand.nextBytes(block);

      long start = System.nanoTime();
      Try<DelaAppendStream> appendOp = storage.appendSession(new TestTimer());
      if (appendOp.isFailure()) {
        return (Try.Failure) appendOp;
      }
      for (int i = 0; i < 100; i++) {
        appendOp.get().write(i * blockSize, block, new AppendCallback());
      }
      Try<Boolean> close = appendOp.get().close();
      if (close.isFailure()) {
        return (Try.Failure) close;
      }
      long end = System.nanoTime();
      long sizeMB = 100 * 10;
      double time = (double) (end - start) / (1000 * 1000 * 1000);
      System.err.println("multiwrite time(s):" + time);
      System.err.println("multiwrite avg speed(MB/s):" + sizeMB / time);
      return new Try.Success(true);
    };
  }

  private void testMultiRead(DelaStorageHandler storage, StorageResource resource) throws Throwable {
    System.err.println("test multi read");
    DelaFileHandler file = prepareFile(storage, resource);
    Try<Boolean> result = new Try.Success(true)
      .flatMap(TryHelper.tryFSucc0(multiAppendFile(file)))
      .flatMap(TryHelper.tryFSucc0(multiReadFile(file)))
      .flatMap(TryHelper.tryFSucc0(() -> file.size()))
      .map(tryAssert((Long size) -> Assert.assertEquals(10 * 100 * 1024 * 1024l, (long) size)))
      .flatMap(TryHelper.tryFSucc0(() -> storage.delete(resource)));
    result.checkedGet();
  }

  private Supplier<Try<Boolean>> multiReadFile(DelaFileHandler storage) {
    return () -> {
      long start = System.nanoTime();
      Try<DelaReadStream> appendSession = storage.readSession(new TestTimer());
      if (appendSession.isFailure()) {
        return (Try.Failure) appendSession;
      }
      for (int i = 0; i < 1000; i++) {
        KRange range = new KBlockImpl(i, i * 1024 * 1024, (i + 1) * 1024 * 1024 - 1);
        appendSession.get().read(range, new ReadCallback());
      }
      Try<Boolean> close = appendSession.get().close();
      if (close.isFailure()) {
        return (Try.Failure) close;
      }
      long end = System.nanoTime();
      long sizeMB = 1000;
      double time = (double) (end - start) / (1000 * 1000 * 1000);
      System.err.println("multiread time(s):" + time);
      System.err.println("multiread avg speed(MB/s):" + sizeMB / time);
      return new Try.Success(true);
    };
  }

  private void testRead(DelaStorageHandler storage, StorageResource resource) throws Throwable {
    System.err.println("test read");
    DelaFileHandler file = prepareFile(storage, resource);
    Try<Boolean> result = new Try.Success(true)
      .flatMap(TryHelper.tryFSucc0(multiAppendFile(file)))
      .flatMap(TryHelper.tryFSucc0(readFile(file)))
      .flatMap(TryHelper.tryFSucc0(() -> file.size()))
      .map(tryAssert((Long size) -> Assert.assertEquals(10 * 100 * 1024 * 1024l, (long) size)))
      .flatMap(TryHelper.tryFSucc0(() -> storage.delete(resource)));
    result.checkedGet();
  }

  private Supplier<Try<Boolean>> readFile(DelaFileHandler storage) {
    return () -> {
      long start = System.nanoTime();
      for (int i = 0; i < 100; i++) {
        KRange range = new KBlockImpl(i, i * 1024 * 1024, (i + 1) * 1024 * 1024 - 1);
        storage.read(range);
      }
      long end = System.nanoTime();
      long sizeMB = 100;
      double time = (double) (end - start) / (1000 * 1000 * 1000);
      System.err.println("read time(s):" + time);
      System.err.println("read avg speed(MB/s):" + sizeMB / time);
      return new Try.Success(true);
    };
  }

  public static class TestTimer implements TimerProxy {

    @Override
    public TimerProxy setup(ComponentProxy proxy) {
      return this;
    }

    @Override
    public UUID schedulePeriodicTimer(long delay, long period, Consumer<Boolean> callback) {
      return UUID.randomUUID();
    }

    @Override
    public void cancelPeriodicTimer(UUID timeoutId) {
    }
  }

  public static class AppendCallback implements Consumer<Try<Boolean>> {

    @Override
    public void accept(Try<Boolean> t) {
      if (t.isFailure()) {
        try {
          ((Try.Failure) t).checkedGet();
        } catch (Throwable ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }

  public static class ReadCallback implements Consumer<Try<byte[]>> {

    @Override
    public void accept(Try<byte[]> t) {
    }
  }
}
