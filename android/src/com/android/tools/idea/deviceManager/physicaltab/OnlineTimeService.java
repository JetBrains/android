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
final class OnlineTimeService {
  private final @NotNull Map<@NotNull String, @Nullable Instant> mySerialNumberToOnlineTimeMap;
  private final @NotNull Clock myClock;

  @SuppressWarnings("unused")
  private OnlineTimeService() {
    this(Clock.systemDefaultZone());
  }

  @VisibleForTesting
  OnlineTimeService(@NotNull Clock clock) {
    mySerialNumberToOnlineTimeMap = new HashMap<>();
    myClock = clock;
  }

  static @NotNull OnlineTimeService getInstance() {
    return ServiceManager.getService(OnlineTimeService.class);
  }

  @NotNull Instant get(@NotNull String serialNumber) {
    return mySerialNumberToOnlineTimeMap.computeIfAbsent(serialNumber, s -> myClock.instant());
  }

  @Nullable Instant remove(@NotNull String serialNumber) {
    return mySerialNumberToOnlineTimeMap.remove(serialNumber);
  }
}
