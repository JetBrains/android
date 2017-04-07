/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;

public abstract class MemoryProfilerTestBase {
  protected StudioProfilers myProfilers;
  protected MemoryProfilerStage myStage;
  protected FakeCaptureObjectLoader myMockLoader;
  protected MemoryAspectObserver myAspectObserver;
  protected FakeTimer myTimer;

  @Before
  public void setupBase() {
    myTimer = new FakeTimer();
    myProfilers = new StudioProfilers(getGrpcChannel().getClient(), new FakeIdeProfilerServices(), myTimer);
    myMockLoader = new FakeCaptureObjectLoader();
    myStage = new MemoryProfilerStage(myProfilers, myMockLoader);
    myAspectObserver = new MemoryAspectObserver(myStage.getAspect());
    onProfilersCreated(myProfilers);
    myProfilers.setStage(myStage);
  }

  /**
   * Child classes are responsible for providing their own fake grpc channel
   */
  protected abstract FakeGrpcChannel getGrpcChannel();

  protected void onProfilersCreated(StudioProfilers profilers) {
  }

  protected static class FakeCaptureObjectLoader extends CaptureObjectLoader {
    @Nullable
    private ListenableFutureTask<CaptureObject> myTask;
    private boolean isReturnImmediateFuture;

    @NotNull
    @Override
    public ListenableFuture<CaptureObject> loadCapture(@NotNull CaptureObject captureObject) {
      if (isReturnImmediateFuture) {
        return Futures.immediateFuture(captureObject);
      }
      else {
        cancelTask();
        myTask = ListenableFutureTask.create(() -> captureObject.load() ? captureObject : null);
        return myTask;
      }
    }

    public void runTask() {
      if (myTask != null) {
        myTask.run();
        myTask = null;
      }
    }

    public void cancelTask() {
      if (myTask != null) {
        myTask.cancel(true);
        myTask = null;
      }
    }

    public void setReturnImmediateFuture(boolean val) {
      isReturnImmediateFuture = val;
      cancelTask();
    }
  }
}
