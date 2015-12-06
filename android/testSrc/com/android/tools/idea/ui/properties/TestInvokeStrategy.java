/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui.properties;

import com.android.tools.idea.ui.properties.BatchInvoker.Strategy;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link Strategy} useful for tests, where a {@link Runnable} given to a {@link BatchInvoker}
 * is postponed until you call {@link #updateOneStep()}. In this way, you can observe how callbacks
 * enqueued into a batch invoker unfold one step at a time.
 */
final class TestInvokeStrategy implements Strategy {
  private Runnable myUpcomingBatch;

  @Override
  public void invoke(@NotNull Runnable runnableBatch) {
    myUpcomingBatch = runnableBatch;
  }

  public void updateOneStep() {
    if (myUpcomingBatch != null) {
      // Assign to a local variable since running this may cause invoke to be called recursively
      Runnable local = myUpcomingBatch;
      myUpcomingBatch = null;
      local.run();
    }
  }
}
