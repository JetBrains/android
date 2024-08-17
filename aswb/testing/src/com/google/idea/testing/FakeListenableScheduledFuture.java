/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.testing;

import com.google.common.util.concurrent.ListenableScheduledFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * A fake listenable future for tests that should be used in concert with {@code
 * FakeListeningScheduledExecutorService}.
 *
 * <p>Users can retrieve the scheduled delay for the future via {@link #getDelay(TimeUnit)}.
 */
public class FakeListenableScheduledFuture<V> implements ListenableScheduledFuture<V> {

  // Future creation parameters
  @Nullable private final Callable<V> callable;
  private final long delay;
  @Nullable private final TimeUnit timeUnit;
  // Execution results
  private boolean isDone = false;
  private V resolvedValue = null;
  private ExecutionException exception = null;
  // Attached listener
  private Runnable listener = null;

  FakeListenableScheduledFuture(Callable<V> callable, long delay, TimeUnit timeUnit) {
    this.callable = callable;
    this.delay = delay;
    this.timeUnit = timeUnit;
  }

  @Override
  public boolean isDone() {
    return isDone;
  }

  @Override
  public V get() throws ExecutionException {
    if (!isDone()) {
      resolve();
      if (listener != null) {
        listener.run();
      }
    }
    return getResolved();
  }

  @Override
  public V get(long l, TimeUnit timeUnit) throws ExecutionException {
    return get();
  }

  @Override
  public boolean cancel(boolean b) {
    return true;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public int compareTo(Delayed delayed) {
    return 0;
  }

  @Override
  public long getDelay(TimeUnit timeUnitTo) {
    return timeUnitTo.convert(delay, timeUnit);
  }

  @Override
  public void addListener(Runnable runnable, Executor executor) {
    listener = runnable;
  }

  private V getResolved() throws ExecutionException {
    if (exception != null) {
      throw exception;
    }
    return resolvedValue;
  }

  private void resolve() {
    try {
      resolvedValue = callable.call();
    } catch (Exception e) {
      exception = new ExecutionException(e);
    }
    isDone = true;
  }
}
