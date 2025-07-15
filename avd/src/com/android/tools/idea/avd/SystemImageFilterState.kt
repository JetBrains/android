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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage

internal class SystemImageFilterState(
  selectedApi: AndroidVersionSelection,
  selectedServices: Services?,
  showSdkExtensionSystemImages: Boolean = false,
  showUnsupportedSystemImages: Boolean = false,
) {
  var selectedApi by mutableStateOf(selectedApi)

  var selectedServices by mutableStateOf(selectedServices)

  var showSdkExtensionSystemImages by mutableStateOf(showSdkExtensionSystemImages)

  var showUnsupportedSystemImages by mutableStateOf(showUnsupportedSystemImages)

  fun filter(
    images: Iterable<ISystemImage>,
    baseExtensionLevels: BaseExtensionLevels = BaseExtensionLevels(images),
  ): List<ISystemImage> {
    return images.filter { image ->
      val apiMatches = selectedApi.matches(image.androidVersion)
      val servicesMatches = selectedServices == null || image.getServices() == selectedServices

      val isSdkExtensionMatches =
        showSdkExtensionSystemImages ||
          image.androidVersion.isPreview ||
          baseExtensionLevels.isBaseExtension(image.androidVersion)

      val isSupportedMatches = showUnsupportedSystemImages || image.isSupported()

      apiMatches && servicesMatches && isSdkExtensionMatches && isSupportedMatches
    }
  }
}

/**
 * Determine the effective base extension level for each API level, within the set of images
 * supplied. This is important for verticals like TV or Auto that may not have any images released
 * at the normal base extension level as defined by [AndroidVersion.ApiBaseExtension]; for these, we
 * want to consider the minimum level among the released images.
 */
internal class BaseExtensionLevels(images: Iterable<ISystemImage>) {
  private val minExtensionLevelMap: Map<AndroidApiLevel, Int?> =
    images
      .map(ISystemImage::getAndroidVersion)
      .filterNot { it.isPreview }
      .groupBy(AndroidVersion::getAndroidApiLevel, AndroidVersion::getExtensionLevel)
      .mapValues { (_, extensionLevels) -> extensionLevels.minWith(nullsFirst<Int>()) }

  fun isBaseExtension(version: AndroidVersion): Boolean {
    val minExtensionLevel = minExtensionLevelMap[version.androidApiLevel]

    return if (minExtensionLevel == null || version.extensionLevel == null) {
      version.isBaseExtension
    } else {
      version.extensionLevel == minExtensionLevel
    }
  }
}
