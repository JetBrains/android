/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.HeapSet;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;

public class CaptureObjectLoaderTest {

  private CaptureObjectLoader myLoader = null;

  @Before
  public void setup() {
    myLoader = new CaptureObjectLoader();
  }

  @Test(expected = AssertionError.class)
  public void loadCaptureBeforeStart() throws Exception {
    TestCaptureObject capture = new TestCaptureObject(new CountDownLatch(1), true, false);
    myLoader.loadCapture(capture, null, null);
  }

  @Test
  public void loadCaptureSuccess() throws Exception {
    myLoader.start();

    CountDownLatch loadLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(1);
    TestCaptureObject capture = new TestCaptureObject(loadLatch, true, false);
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(capture, null, null);

    future.addListener(() -> {
      try {
        CaptureObject loadedCapture = future.get();
        assertEquals(capture, loadedCapture);
      }
      catch (InterruptedException | ExecutionException | CancellationException exception) {
        assert false;
      }
      finally {
        doneLatch.countDown();
      }
    }, MoreExecutors.directExecutor());

    loadLatch.countDown();
    doneLatch.await();
  }

  @Test
  public void loadCaptureFailure() throws Exception {
    myLoader.start();

    CountDownLatch loadLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(1);
    TestCaptureObject capture = new TestCaptureObject(loadLatch, false, false);
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(capture, null, null);

    future.addListener(() -> {
      try {
        assertNull(future.get());
      }
      catch (InterruptedException | ExecutionException | CancellationException exception) {
        assert false;
      }
      finally {
        doneLatch.countDown();
      }
    }, MoreExecutors.directExecutor());

    loadLatch.countDown();
    doneLatch.await();
  }

  @Test
  public void loadCaptureException() throws Exception {
    myLoader.start();

    CountDownLatch loadLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(1);
    TestCaptureObject capture = new TestCaptureObject(loadLatch, true, false);
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(capture, null, null);

    future.addListener(() -> {
      try {
        future.get();
        assert false;
      }
      catch (ExecutionException exception) {
        // No-op - expected path.
      }
      catch (CancellationException | InterruptedException ignored) {
        assert false;
      }
      finally {
        doneLatch.countDown();
      }
    }, MoreExecutors.directExecutor());

    loadLatch.countDown();
    doneLatch.await();
  }

  @Test
  public void loadCaptureCancel() throws Exception {
    myLoader.start();

    CountDownLatch loadLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(1);
    TestCaptureObject capture = new TestCaptureObject(loadLatch, true, false);
    ListenableFuture<CaptureObject> future = myLoader.loadCapture(capture, null, null);

    future.addListener(() -> {
      try {
        future.get();
        assert false;
      }
      catch (CancellationException ignored) {
        // No-op - expected path.
      }
      catch (InterruptedException | ExecutionException exception) {
        assert false;
      }
      finally {
        doneLatch.countDown();
      }
    }, MoreExecutors.directExecutor());

    myLoader.stop();
    doneLatch.await();
  }

  private static class TestCaptureObject implements CaptureObject {
    private final CountDownLatch myLoadLatch;
    private final boolean myLoadSuccessful;
    private final boolean myThrowsException;

    public TestCaptureObject(@NotNull CountDownLatch loadLatch, boolean loadSuccessful, boolean throwsException) {
      myLoadLatch = loadLatch;
      myLoadSuccessful = loadSuccessful;
      myThrowsException = throwsException;
    }

    @NotNull
    @Override
    public String getName() {
      return "";
    }

    @Nullable
    @Override
    public String getExportableExtension() {
      return null;
    }

    @Override
    public void saveToFile(@NotNull OutputStream outputStream) {
    }

    @NotNull
    @Override
    public List<ClassifierAttribute> getClassifierAttributes() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public List<InstanceAttribute> getInstanceAttributes() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<HeapSet> getHeapSets() {
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public HeapSet getHeapSet(int heapId) {
      return null;
    }

    @NotNull
    @Override
    public Stream<InstanceObject> getInstances() {
      return Stream.empty();
    }

    @Override
    public long getStartTimeNs() {
      return 0;
    }

    @Override
    public long getEndTimeNs() {
      return 0;
    }

    @Override
    public boolean load(@Nullable Range queryRange, @Nullable Executor queryJoiner) {
      try {
        myLoadLatch.await();
      }
      catch (InterruptedException ignored) {
      }

      if (myThrowsException) {
        throw new RuntimeException();
      }

      return myLoadSuccessful;
    }

    @Override
    public boolean isDoneLoading() {
      return false;
    }

    @Override
    public boolean isError() {
      return false;
    }

    @Override
    public void unload() {

    }
  }
}