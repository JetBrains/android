/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import com.android.annotations.concurrency.GuardedBy;
import com.intellij.openapi.Disposable;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * A wrapper around {@link MergingUpdateQueue} for thread-safe task scheduling. Used for scheduling render calls.
 */
public class MergingRenderingQueue implements RenderingQueue {
  @GuardedBy("this")
  private MergingUpdateQueue myRenderingQueue;
  private static final int RENDER_DELAY_MS = 10;

  /**
   * {@link Consumer} called when setting up the Rendering {@link MergingUpdateQueue} to do additional setup. This can be used for
   * additional setup required for testing.
   */
  @NotNull private final Disposable myParentDisposable;

  MergingRenderingQueue(@NotNull Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
  }

  @NotNull
  private synchronized MergingUpdateQueue getRenderingQueue() {
    if (myRenderingQueue == null) {
      myRenderingQueue = new MergingUpdateQueue("android.layout.rendering", RENDER_DELAY_MS, true, null, myParentDisposable, null,
                                                Alarm.ThreadToUse.POOLED_THREAD);
      myRenderingQueue.setRestartTimerOnAdd(true);
    }
    return myRenderingQueue;
  }

  @Override
  public synchronized void deactivate() {
    if (myRenderingQueue != null) {
      myRenderingQueue.cancelAllUpdates();
    }
  }

  @Override
  public void queue(@NotNull Update update) {
    getRenderingQueue().queue(update);
  }
}
