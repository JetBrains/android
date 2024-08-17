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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Fake implementation for {@link ListeningScheduledExecutorService} which allows executing
 * controllable from the test and not by timer. A Minimal number of methods required for testing has
 * been implemented.
 */
public final class FakeListeningScheduledExecutorService
    implements ListeningScheduledExecutorService {

  @Nullable
  public FakeListenableScheduledFuture<?> getNextExecution() {
    return nextExecution;
  }

  @Nullable FakeListenableScheduledFuture<?> nextExecution = null;

  public void runNext() throws Exception {
    if (nextExecution == null) {
      return;
    }
    nextExecution.get();
  }

  @Override
  public <V> ListenableScheduledFuture<V> schedule(
      Callable<V> callable, long delay, TimeUnit timeUnit) {
    FakeListenableScheduledFuture<V> future =
        new FakeListenableScheduledFuture<>(callable, delay, timeUnit);
    nextExecution = future;
    return future;
  }

  @Override
  public ListenableScheduledFuture<?> schedule(Runnable runnable, long l, TimeUnit timeUnit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ListenableScheduledFuture<?> scheduleAtFixedRate(
      Runnable runnable, long l, long l1, TimeUnit timeUnit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ListenableScheduledFuture<?> scheduleWithFixedDelay(
      Runnable runnable, long initialDelay, long delay, TimeUnit timeUnit) {
    FakeListenableScheduledFuture<Runnable> future =
        new FakeListenableScheduledFuture<>(
            () -> {
              runnable.run();
              return null;
            },
            delay,
            timeUnit);
    nextExecution = future;
    return future;
  }

  @Override
  public void shutdown() {}

  @Override
  public List<Runnable> shutdownNow() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long l, TimeUnit timeUnit) {
    return false;
  }

  @Override
  public <T> ListenableFuture<T> submit(Callable<T> callable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ListenableFuture<?> submit(Runnable runnable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> ListenableFuture<T> submit(Runnable runnable, T t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> collection) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> collection) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void execute(Runnable runnable) {
    throw new UnsupportedOperationException();
  }
}
