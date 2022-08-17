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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.annotations.concurrency.Slow;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ProcessManager {
  private Object myKeyToStateMap;
  private final @NotNull Supplier<@NotNull AvdManagerConnection> myGetDefaultAvdManagerConnection;

  ProcessManager() {
    this(AvdManagerConnection::getDefaultAvdManagerConnection);
  }

  @VisibleForTesting
  ProcessManager(@NotNull Supplier<@NotNull AvdManagerConnection> getDefaultAvdManagerConnection) {
    myGetDefaultAvdManagerConnection = getDefaultAvdManagerConnection;
  }

  @NotNull ListenableFuture<@Nullable Void> initAsync() {
    // noinspection UnstableApiUsage
    return Futures.submit(this::init, AppExecutorUtil.getAppExecutorService());
  }

  @Slow
  private void init() {
    AvdManagerConnection connection = myGetDefaultAvdManagerConnection.get();

    myKeyToStateMap = connection.getAvds(true).stream()
      .collect(Collectors.toMap(avd -> new VirtualDevicePath(avd.getId()), avd -> State.valueOf(connection.isAvdRunning(avd))));
  }

  @VisibleForTesting
  enum State {
    STOPPED, LAUNCHED;

    private static @NotNull State valueOf(boolean online) {
      if (online) {
        return LAUNCHED;
      }

      return STOPPED;
    }
  }

  @VisibleForTesting
  @NotNull Object getKeyToStateMap() {
    return myKeyToStateMap;
  }
}
