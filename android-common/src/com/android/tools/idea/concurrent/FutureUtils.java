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
package com.android.tools.idea.concurrent;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * Static utility methods pertaining to the {@link java.util.concurrent.Future Future} interface.
 */
public class FutureUtils {
  /**
   * Similar to {@link Application#executeOnPooledThread(Callable)} but returns a {@link ListenableFuture}.
   *
   * @param action the action to be executed
   * @return future result
   * @see Application#executeOnPooledThread(Callable)
   */
  @NotNull
  public static <T> ListenableFuture<T> executeOnPooledThread(@NotNull Callable<T> action) {
    ListenableFutureTask<T> futureTask = ListenableFutureTask.create(action);
    ApplicationManager.getApplication().executeOnPooledThread(futureTask);
    return futureTask;
  }
}
