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

import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.google.common.util.concurrent.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureObjectLoader {
  @Nullable private ExecutorService myExecutorService = null;

  @Nullable
  private ListenableFutureTask<CaptureObject> myOutstandingLoadingTask = null;

  @NotNull
  public ListenableFuture<CaptureObject> loadCapture(@NotNull CaptureObject captureObject,
                                                     @Nullable Range queryRange,
                                                     @Nullable Executor queryJoiner) {
    assert myExecutorService != null;
    cancelTask();

    ListenableFutureTask<CaptureObject> task =
      ListenableFutureTask.create(() -> captureObject.load(queryRange, queryJoiner) ? captureObject : null);
    myOutstandingLoadingTask = task;

    Futures.addCallback(task, new FutureCallback<CaptureObject>() {
      @Override
      public void onSuccess(@Nullable CaptureObject result) {
        removeTask();
      }

      @Override
      public void onFailure(@NotNull Throwable ignored) {
        removeTask();
      }

      private void removeTask() {
        myOutstandingLoadingTask = null;
      }
    }, MoreExecutors.directExecutor());

    myExecutorService.execute(task);
    return task;
  }

  public void start() {
    if (myExecutorService == null) {
      myExecutorService =
        Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("profiler-capture-object-loader").build());
    }
  }

  public void stop() {
    cancelTask();
    if (myExecutorService != null) {
      myExecutorService.shutdownNow();
      myExecutorService = null;
    }
  }

  private void cancelTask() {
    if (myOutstandingLoadingTask != null) {
      myOutstandingLoadingTask.cancel(true);
      myOutstandingLoadingTask = null;
    }
  }
}
