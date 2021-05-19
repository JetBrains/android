/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.annotations.concurrency.AnyThread;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;

@Service
final class DeviceSkinUpdaterService {
  private final @NotNull ListeningExecutorService myExecutorService;

  private DeviceSkinUpdaterService() {
    // DeviceSkinUpdaterService being application level and this executor ensure that only a single thread calls
    // DeviceSkinUpdater::updateSkins regardless of how many threads call DeviceSkinUpdaterService::updateSkins. That is desirable because
    // DeviceSkinUpdater::updateSkins can modify the file system.
    ExecutorService delegate = AppExecutorUtil.createBoundedApplicationPoolExecutor("DeviceSkinUpdaterService", 1);

    myExecutorService = MoreExecutors.listeningDecorator(delegate);
  }

  static @NotNull DeviceSkinUpdaterService getInstance() {
    return ServiceManager.getService(DeviceSkinUpdaterService.class);
  }

  /**
   * Usually returns the SDK skins path for the device (${HOME}/Android/Sdk/skins/pixel_4). This method also copies device skins from Studio
   * to the SDK if the SDK ones are out of date and converts WebP skin images to PNG if the emulator doesn't support WebP images.
   *
   * @return the SDK skins path for the device. Returns device as is if both the Studio skins path and the SDK are not found. Returns the
   * SDK skins path for the device if the Studio skins path is not found. Returns the Studio skins path for the device
   * (${HOME}/android-studio/plugins/android/lib/device-art-resources/pixel_4) if the SDK is not found or an IOException is thrown.
   */
  @AnyThread
  @NotNull ListenableFuture<@NotNull Path> updateSkins(@NotNull Path device) {
    return myExecutorService.submit(() -> DeviceSkinUpdater.updateSkins(device));
  }
}
