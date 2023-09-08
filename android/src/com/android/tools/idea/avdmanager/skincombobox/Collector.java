/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.skincombobox;

import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.avdmanager.DeviceSkinUpdater;
import com.android.tools.idea.avdmanager.DeviceSkinUpdaterService;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * Collects the platform skins, the system image skins, and the rest; and wraps them in {@link Skin} instances
 */
final class Collector {
  @NotNull
  private final AndroidSdkHandler myHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();

  private final ProgressIndicator myIndicator = new StudioLoggerProgressIndicator(Collector.class);

  @NotNull
  Collection<Skin> collect() {
    return Streams.concat(platformSkins(), skins(), systemImageSkins()).toList();
  }

  @NotNull
  private Stream<Skin> platformSkins() {
    return myHandler.getAndroidTargetManager(myIndicator).getTargets(myIndicator).stream().flatMap(Collector::platformSkins);
  }

  @NotNull
  private static Stream<Skin> platformSkins(@NotNull IAndroidTarget platform) {
    var version = platform.getVersion();

    return Arrays.stream(platform.getSkins())
      .map(DeviceSkinUpdater::updateSkins)
      .map(path -> new PlatformSkin(path, version));
  }

  @NotNull
  private static Stream<Skin> skins() {
    return DeviceSkinUpdaterService.deviceSkinStream().map(DefaultSkin::new);
  }

  @NotNull
  private Stream<Skin> systemImageSkins() {
    return myHandler.getSystemImageManager(myIndicator).getImages().stream().flatMap(Collector::systemImageSkins);
  }

  @NotNull
  private static Stream<Skin> systemImageSkins(@NotNull ISystemImage image) {
    var version = image.getAndroidVersion();
    var abi = image.getAbiType();

    return Arrays.stream(image.getSkins())
      .map(DeviceSkinUpdater::updateSkins)
      .map(path -> new SystemImageSkin(path, version, abi));
  }
}
