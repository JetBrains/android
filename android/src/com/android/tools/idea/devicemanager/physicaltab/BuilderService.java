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
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice.ConnectionType;
import com.android.tools.idea.util.Targets;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.concurrency.EdtExecutorService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
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
    return ServiceManager.getService(BuilderService.class);
  }

  @NotNull ListenableFuture<@NotNull PhysicalDevice> build(@NotNull IDevice device) {
    ListenableFuture<String> modelFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MODEL);
    ListenableFuture<String> manufacturerFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER);

    Executor executor = EdtExecutorService.getInstance();

    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(modelFuture, manufacturerFuture).call(() -> build(device, modelFuture, manufacturerFuture), executor);
  }

  private @NotNull PhysicalDevice build(@NotNull IDevice device,
                                        @NotNull Future<@NotNull String> modelFuture,
                                        @NotNull Future<@NotNull String> manufacturerFuture) {
    String keyValue = device.getSerialNumber();

    boolean domainName = DeviceUtils.isMdnsAutoConnectTls(keyValue);
    boolean online = device.isOnline();

    Key key = domainName ? new DomainName(keyValue) : new SerialNumber(keyValue);
    Instant time;

    if (online) {
      time = myKeyToOnlineTimeMap.computeIfAbsent(key, k -> myClock.instant());
    }
    else {
      time = myKeyToOnlineTimeMap.remove(key);
    }

    AndroidVersion version = device.getVersion();

    PhysicalDevice.Builder builder = new PhysicalDevice.Builder()
      .setKey(key)
      .setLastOnlineTime(time)
      .setName(DeviceNameProperties.getName(FutureUtils.getDoneOrNull(modelFuture), FutureUtils.getDoneOrNull(manufacturerFuture)))
      .setTarget(Targets.toString(version))
      .setApi(version.getApiString());

    if (online) {
      builder.addConnectionType(domainName ? ConnectionType.WI_FI : ConnectionType.USB);
    }

    return builder.build();
  }
}
