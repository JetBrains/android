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
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage

internal class SystemImageFilterState
internal constructor(
  selectedApi: AndroidVersionSelection,
  selectedServices: Services?,
  showSdkExtensionSystemImages: Boolean = false,
  showUnsupportedSystemImages: Boolean = false,
) {
  internal var selectedApi by mutableStateOf(selectedApi)
    private set

  internal var selectedServices by mutableStateOf(selectedServices)
    private set

  internal var showSdkExtensionSystemImages by mutableStateOf(showSdkExtensionSystemImages)
    private set

  internal var showUnsupportedSystemImages by mutableStateOf(showUnsupportedSystemImages)
    private set

  internal fun setSelectedApi(selectedApi: AndroidVersionSelection) {
    this.selectedApi = selectedApi
  }

  internal fun setSelectedServices(selectedServices: Services?) {
    this.selectedServices = selectedServices
  }

  internal fun setShowSdkExtensionSystemImages(showSdkExtensionSystemImages: Boolean) {
    this.showSdkExtensionSystemImages = showSdkExtensionSystemImages
  }

  internal fun setShowUnsupportedSystemImages(showUnsupportedSystemImages: Boolean) {
    this.showUnsupportedSystemImages = showUnsupportedSystemImages
  }

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

internal class BaseExtensionLevels(images: Iterable<ISystemImage>) {
  private val comparator = Comparator.nullsFirst<Int?>(Comparator.naturalOrder())
  private val minExtensionLevelMap =
    images
      .map(ISystemImage::getAndroidVersion)
      .groupBy(::ApiLevelAndCodename, AndroidVersion::getExtensionLevel)
      .mapValues { (_, extensionLevels) -> extensionLevels.minWith(comparator) }

  fun isBaseExtension(version: AndroidVersion): Boolean {
    val minExtensionLevel = minExtensionLevelMap[ApiLevelAndCodename(version)]

    return if (minExtensionLevel == null) {
      // This branch protects against multiple base extensions with the same API level but
      // differing extension levels (null and the expected nonnull value). The null would hide the
      // nonnull value and the nonnull value would not be reported as a base extension.
      version.isBaseExtension
    } else {
      version.extensionLevel == minExtensionLevel
    }
  }
}

private data class ApiLevelAndCodename(private val apiLevel: Int, private val codename: String?) {
  constructor(version: AndroidVersion) : this(version.apiLevel, version.codename)
}
