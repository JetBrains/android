/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.Feature;
import com.android.ddmlib.IDevice.HardwareFeature;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.avdmanager.EmulatorAdvFeatures;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.DeviceStateCache;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.utils.ILogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * The production implementation of Features uses a DeviceStateCache.
 */
final class CachedFeatures implements Features {
  private final DeviceStateCache<CompletableFuture<DeviceFeatures>> myCache;

  CachedFeatures(@NotNull Disposable parent) {
    myCache = new DeviceStateCache<>(parent);
  }

  @Override
  public boolean watch(@NotNull IDevice device) {
    return getFeatures(device).myWatch;
  }

  @Override
  public boolean screenRecord(@NotNull IDevice device) {
    if (device.isEmulator()) {
      AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
      ProgressIndicator indicator = new StudioLoggerProgressIndicator(CachedFeatures.class);
      ILogger logger = new LogWrapper(Logger.getInstance(CachedFeatures.class));

      return EmulatorAdvFeatures.emulatorSupportsScreenRecording(handler, indicator, logger);
    }

    return getFeatures(device).myScreenRecord;
  }

  @NotNull
  private DeviceFeatures getFeatures(@NotNull IDevice device) {
    CompletableFuture<DeviceFeatures> features = myCache.get(device, "");

    if (features == null) {
      features = CompletableFuture.supplyAsync(() -> new DeviceFeatures(device));
      myCache.put(device, "", features);
    }

    return features.getNow(new DeviceFeatures());
  }

  private static final class DeviceFeatures {
    private final boolean myWatch;
    private final boolean myScreenRecord;

    private DeviceFeatures() {
      this(false, false);
    }

    private DeviceFeatures(@NotNull IDevice device) {
      this(device.supportsFeature(HardwareFeature.WATCH), device.supportsFeature(Feature.SCREEN_RECORD));
    }

    private DeviceFeatures(boolean watch, boolean screenRecord) {
      myWatch = watch;
      myScreenRecord = screenRecord;
    }
  }
}
