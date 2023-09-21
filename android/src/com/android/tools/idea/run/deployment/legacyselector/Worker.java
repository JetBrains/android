/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import com.intellij.openapi.diagnostic.Logger;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Worker<V> {
  @Nullable
  private Future<V> myResultFuture;

  @Nullable
  private V myResult;

  @NotNull
  synchronized Optional<V> perform(@NotNull AsyncSupplier<V> task) {
    if (myResultFuture == null) {
      myResultFuture = task.get();
    }

    if (myResultFuture.isCancelled()) {
      myResultFuture = task.get();
      return Optional.ofNullable(myResult);
    }

    if (!myResultFuture.isDone()) {
      return Optional.ofNullable(myResult);
    }

    try {
      // noinspection BlockingMethodInNonBlockingContext At this point myResultFuture.isDone() is true so myResultFuture.get() can't block
      myResult = myResultFuture.get();
      assert myResult != null;

      myResultFuture = task.get();
      return Optional.of(myResult);
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(exception);
    }
    catch (ExecutionException exception) {
      Logger.getInstance(Worker.class).warn(exception);

      myResultFuture = task.get();
      return Optional.ofNullable(myResult);
    }
  }
}
