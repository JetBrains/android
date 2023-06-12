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
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.SerialNumber;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

@UiThread
@Service
final class BuilderService {
  @VisibleForTesting
  BuilderService() {
  }

  static @NotNull BuilderService getInstance() {
    return ApplicationManager.getApplication().getService(BuilderService.class);
  }

  @NotNull ListenableFuture<PhysicalDevice> build(@NotNull IDevice device) {
    return new AsyncPhysicalDeviceBuilder(device, parse(device.getSerialNumber())).buildAsync();
  }

  private static @NotNull Key parse(@NotNull String value) {
    if (DeviceUtils.isMdnsAutoConnectTls(value)) {
      return new DomainName(value);
    }

    return Ipv4Address.parse(value)
      .or(() -> Localhost.parse(value))
      .orElseGet(() -> new SerialNumber(value));
  }
}
