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

import com.android.annotations.Nullable;
import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

@Service
public final class DeviceSkinUpdaterService {
  private final @NotNull ListeningExecutorService myExecutorService;

  private DeviceSkinUpdaterService() {
    // DeviceSkinUpdaterService being application level and this executor ensure that only a single thread calls
    // DeviceSkinUpdater::updateSkins regardless of how many threads call the updateSkins methods in this class. That is desirable because
    // DeviceSkinUpdater::updateSkins can modify the file system.
    ExecutorService delegate = AppExecutorUtil.createBoundedApplicationPoolExecutor("DeviceSkinUpdaterService", 1);

    myExecutorService = MoreExecutors.listeningDecorator(delegate);
  }

  public static @NotNull DeviceSkinUpdaterService getInstance() {
    return ApplicationManager.getApplication().getService(DeviceSkinUpdaterService.class);
  }

  @NotNull Executor getExecutor() {
    return myExecutorService;
  }

  @AnyThread
  @SuppressWarnings("unused")
  @NotNull ListenableFuture<Path> updateSkins(@NotNull Path device) {
    return updateSkins(device, null);
  }

  @AnyThread
  public @NotNull ListenableFuture<Path> updateSkins(@NotNull Path device,
                                                              @Nullable @SuppressWarnings("SameParameterValue") SystemImageDescription image) {
    return myExecutorService.submit(() -> DeviceSkinUpdater.updateSkins(device, image));
  }

  @AnyThread
  @NotNull ListenableFuture<Collection<Path>> updateSkinsIncludingSdkHandlerOnes() {
    return myExecutorService.submit(() -> updateSkins(DeviceSkinUpdater::updateSkins));
  }

  @AnyThread
  @NotNull ListenableFuture<Collection<Path>> updateSkinsExcludingSdkHandlerOnes() {
    return myExecutorService.submit(() -> updateSkins(Path::getFileName));
  }

  @WorkerThread
  private static @NotNull Collection<Path> updateSkins(@NotNull Function<Path, Path> updateSkins) {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    ProgressIndicator indicator = new StudioLoggerProgressIndicator(DeviceSkinUpdaterService.class);

    Stream<Path> stream = Streams.concat(deviceSkinStream(),
                                         targetSkinStream(handler, indicator, updateSkins),
                                         imageSkinStream(handler, indicator, updateSkins));

    return stream.collect(Collectors.toList());
  }

  @WorkerThread
  private static @NotNull Stream<Path> deviceSkinStream() {
    return DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevices().stream()
      .map(Device::getDefaultHardware)
      .map(Hardware::getSkinFile)
      .filter(Objects::nonNull)
      .map(File::toPath)
      .map(DeviceSkinUpdater::updateSkins);
  }

  @WorkerThread
  private static @NotNull Stream<Path> targetSkinStream(@NotNull AndroidSdkHandler handler,
                                                                 @NotNull ProgressIndicator indicator,
                                                                 @NotNull Function<Path, Path> updateSkins) {
    return handler.getAndroidTargetManager(indicator).getTargets(indicator).stream()
      .map(IAndroidTarget::getSkins)
      .flatMap(Arrays::stream)
      .filter(Objects::nonNull)
      .map(updateSkins);
  }

  @WorkerThread
  private static @NotNull Stream<Path> imageSkinStream(@NotNull AndroidSdkHandler handler,
                                                                @NotNull ProgressIndicator indicator,
                                                                @NotNull Function<Path, Path> updateSkins) {
    return handler.getSystemImageManager(indicator).getImages().stream()
      .map(SystemImage::getSkins)
      .flatMap(Arrays::stream)
      .filter(Objects::nonNull)
      .map(updateSkins);
  }
}
