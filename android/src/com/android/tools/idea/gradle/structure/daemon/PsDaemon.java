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
package com.android.tools.idea.gradle.structure.daemon;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class PsDaemon implements Disposable {
  @NotNull private final PsContext myContext;
  @NotNull private final AtomicBoolean myStopped = new AtomicBoolean(false);

  protected PsDaemon(@NotNull PsContext context) {
    myContext = context;
    Disposer.register(context, this);
  }

  @NotNull
  protected final MergingUpdateQueue createQueue(@NotNull String name, @Nullable JComponent modalityStateComponent) {
    return new MergingUpdateQueue(name, 300, false, modalityStateComponent, this, null, Alarm.ThreadToUse.POOLED_THREAD);
  }

  public void reset() {
    MergingUpdateQueue mainQueue = getMainQueue();
    reset(mainQueue, getResultsUpdaterQueue());
    mainQueue.queue(new Update("reset") {
      @Override
      public void run() {
        myStopped.set(false);
      }
    });
  }

  private static void reset(@NotNull MergingUpdateQueue... queues) {
    for (MergingUpdateQueue queue : queues) {
      queue.activate();
    }
  }

  public void stop() {
    myStopped.set(true);
    stop(getMainQueue(), getResultsUpdaterQueue());
  }

  private static void stop(@NotNull MergingUpdateQueue... queues) {
    for (MergingUpdateQueue queue : queues) {
      queue.cancelAllUpdates();
      queue.deactivate();
    }
  }

  @NotNull
  protected abstract MergingUpdateQueue getMainQueue();

  @NotNull
  protected abstract MergingUpdateQueue getResultsUpdaterQueue();

  protected boolean isStopped() {
    return myStopped.get();
  }

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }

  @Override
  public void dispose() {
    stop();
  }
}
