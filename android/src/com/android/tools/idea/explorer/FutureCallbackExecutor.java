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
package com.android.tools.idea.explorer;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * An {@link Executor} implementation that registers {@link ListenableFuture} callbacks
 * to be executed with itself via the {@link #addCallback(ListenableFuture, FutureCallback)} method.
 *
 * The goal is to act as an alternative to {@link Futures#addCallback(ListenableFuture, FutureCallback)}
 * to make the <code>executor</code> parameter explicit.
 */
public class FutureCallbackExecutor implements Executor {
  private final Executor myExecutor;

  public FutureCallbackExecutor(Executor executor) {
    myExecutor = executor;
  }

  @Override
  public void execute(@NotNull Runnable command) {
    myExecutor.execute(command);
  }

  /**
   * Add a callback to a {@link ListenableFuture} with ourselves as the executor.
   */
  public <V> void addCallback(final ListenableFuture<V> future,
                              final FutureCallback<? super V> callback) {
    Futures.addCallback(future, callback, this);
  }
}
