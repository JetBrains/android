/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceManagerFutures {
  private DeviceManagerFutures() {
  }

  public static <V> @NotNull ListenableFuture<@Nullable V> appExecutorServiceSubmit(@NotNull Callable<@Nullable V> callable) {
    return Futures.submit(callable, AppExecutorUtil.getAppExecutorService());
  }

  public static @NotNull ListenableFuture<@Nullable Void> appExecutorServiceSubmit(@NotNull Runnable runnable) {
    return Futures.submit(runnable, AppExecutorUtil.getAppExecutorService());
  }

  public static <V> @NotNull V getDoneOrElse(@NotNull Future<@NotNull V> future, @NotNull V defaultValue) {
    assert future.isDone();

    try {
      return future.get();
    }
    catch (CancellationException | ExecutionException exception) {
      return defaultValue;
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  public static <V> @NotNull ListenableFuture<@NotNull List<@NotNull V>> successfulAsList(@NotNull Iterable<@NotNull ListenableFuture<@NotNull V>> futures,
                                                                                          @NotNull Executor executor) {
    // noinspection UnstableApiUsage
    return Futures.transform(Futures.successfulAsList(futures), DeviceManagerFutures::filterSuccessful, executor);
  }

  private static <V> @NotNull List<@NotNull V> filterSuccessful(@NotNull Collection<@Nullable V> values) {
    List<V> nonnullValues = values.stream()
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    if (nonnullValues.size() != values.size()) {
      Logger.getInstance(DeviceManagerFutures.class).warn("Some of the input futures failed");
    }

    return nonnullValues;
  }
}
