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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.annotations.concurrency.UiThread;
import com.android.ddmlib.IDevice;
import com.android.ide.common.util.DeviceUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.serviceContainer.NonInjectable;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UiThread
@Service
final class BuilderService {
  private final @NotNull Map<@NotNull Key, @Nullable Instant> myKeyToOnlineTimeMap;
  private final @NotNull Clock myClock;

  @SuppressWarnings("unused")
  private BuilderService() {
    this(Clock.systemDefaultZone());
  }

  @VisibleForTesting
  @NonInjectable
  BuilderService(@NotNull Clock clock) {
    myKeyToOnlineTimeMap = new HashMap<>();
    myClock = clock;
  }

  static @NotNull BuilderService getInstance() {
    return ApplicationManager.getApplication().getService(BuilderService.class);
  }

  @NotNull ListenableFuture<@NotNull PhysicalDevice> build(@NotNull IDevice device) {
    Instant time;

    String value = device.getSerialNumber();
    Key key = DeviceUtils.isMdnsAutoConnectTls(value) ? new DomainName(value) : new SerialNumber(value);

    if (device.isOnline()) {
      time = myKeyToOnlineTimeMap.computeIfAbsent(key, k -> myClock.instant());
    }
    else {
      time = myKeyToOnlineTimeMap.remove(key);
    }

    return new AsyncPhysicalDeviceBuilder(device, key, time).buildAsync();
  }
}
