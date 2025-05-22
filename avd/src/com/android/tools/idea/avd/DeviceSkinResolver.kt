/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.android.tools.idea.avdmanager.SkinUtils
import java.nio.file.Path

internal object DeviceSkinResolver {
  /**
   * If a system image skin has the same suffix as deviceSkin (including separators), returns the
   * system image skin. Otherwise, resolves deviceSkin against sdk or deviceArtResources (depending
   * on which one is null). If resolving against deviceArtResources, also renames a handful of Wear
   * OS skins.
   *
   * @param deviceSkin the device definition skin
   * @param imageSkins the system image skins
   * @param sdk the path to the Android SDK
   * @param deviceArtResources the device-art-resources subdirectory, under the Android Studio
   *   application directory, containing the skins shipped with Studio
   * @return an absolute path if imageSkins, sdk, and deviceArtResources are absolute. Returns
   *   deviceSkin as is if it's already absolute, the empty path, _no_skin, or if both sdk and
   *   deviceArtResources are null.
   */
  fun resolve(
    deviceSkin: Path,
    imageSkins: Iterable<Path>,
    sdk: Path?,
    deviceArtResources: Path?,
  ): Path {
    when {
      deviceSkin.isAbsolute -> return deviceSkin
      deviceSkin == Path.of("") -> return deviceSkin
      deviceSkin == SkinUtils.noSkin() -> return deviceSkin
    }

    val imageSkin = imageSkins.find { it.endsWith(deviceSkin) }

    if (imageSkin != null) {
      return imageSkin
    }

    return when {
      sdk != null -> sdk.resolve("skins").resolve(deviceSkin)
      deviceArtResources != null -> deviceArtResources.resolve(convertWearOsFileName(deviceSkin))
      else -> deviceSkin
    }
  }

  private fun convertWearOsFileName(deviceSkin: Path): Path =
    when (deviceSkin.fileName) {
      Path.of("WearLargeRound") -> Path.of("wearos_large_round")
      Path.of("WearRect") -> Path.of("wearos_rect")
      Path.of("WearSmallRound") -> Path.of("wearos_small_round")
      Path.of("WearSquare") -> Path.of("wearos_square")
      else -> deviceSkin.fileName
    }
}
