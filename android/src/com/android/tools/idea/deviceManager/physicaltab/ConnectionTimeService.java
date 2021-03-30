/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.physicaltab;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
final class ConnectionTimeService {
  private final @NotNull Map<@NotNull String, @Nullable Instant> mySerialNumberToConnectionTimeMap;
  private final @NotNull Clock myClock;

  @SuppressWarnings("unused")
  private ConnectionTimeService() {
    this(Clock.systemDefaultZone());
  }

  @VisibleForTesting
  ConnectionTimeService(@NotNull Clock clock) {
    mySerialNumberToConnectionTimeMap = new HashMap<>();
    myClock = clock;
  }

  static @NotNull ConnectionTimeService getInstance() {
    return ServiceManager.getService(ConnectionTimeService.class);
  }

  @NotNull Instant get(@NotNull String serialNumber) {
    return mySerialNumberToConnectionTimeMap.computeIfAbsent(serialNumber, s -> myClock.instant());
  }

  @Nullable Instant remove(@NotNull String serialNumber) {
    return mySerialNumberToConnectionTimeMap.remove(serialNumber);
  }
}
