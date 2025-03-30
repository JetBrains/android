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
package com.android.tools.idea.observable;

import com.google.common.collect.Queues;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link BatchInvoker.Strategy} useful for tests, where a {@link Runnable} given to a {@link BatchInvoker}
 * is postponed until you call {@link #updateOneStep()} or {@link #updateAllSteps()}. In this way, you can observe how callbacks
 * enqueued into a batch invoker unfold in a controlled manner.
 */
public final class TestInvokeStrategy implements BatchInvoker.Strategy {

  /**
   * A Queue of runnables, each runnable representing a batch of callbacks to be executed asynchronously.
   */
  public Queue<Runnable> myBatchQueue = Queues.newArrayDeque();

  @Override
  public void invoke(@NotNull Runnable batch) {
    myBatchQueue.add(batch);
  }

  /**
   * Runs a single batch of callbacks if there are any queued.
   */
  public void updateOneStep() {
    Runnable batch = myBatchQueue.poll();
    if (batch != null) {
      batch.run();
    }
  }

  /**
   * Runs a number of batches of callbacks until the invocation queue is depleted
   */
  public void updateAllSteps() {
    Runnable batch = myBatchQueue.poll();
    while (batch != null) {
      batch.run();
      batch = myBatchQueue.poll();
    }
  }
}
