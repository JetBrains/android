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
package com.android.tools.idea.run.deployment;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Worker<V> {
  @Nullable
  private Future<V> myResultFuture;

  @NotNull
  private V myResult;

  Worker(@NotNull V defaultResult) {
    myResult = defaultResult;
  }

  @NotNull
  V get(@NotNull Callable<V> task) {
    Application application = ApplicationManager.getApplication();

    if (myResultFuture == null) {
      myResultFuture = application.executeOnPooledThread(task);
    }

    if (!myResultFuture.isDone()) {
      return myResult;
    }

    try {
      myResult = myResultFuture.get();
      myResultFuture = application.executeOnPooledThread(task);

      return myResult;
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(exception);
    }
    catch (ExecutionException exception) {
      throw new RuntimeException(exception);
    }
  }
}
