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
package com.android.tools.idea.compose.pickers.preview.tracking

import com.android.resources.Density
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.compose.pickers.preview.property.DeviceConfig
import com.android.tools.idea.compose.pickers.preview.property.DimUnit
import com.android.tools.idea.compose.pickers.preview.property.toMutableConfig
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import com.intellij.openapi.diagnostic.Logger
import kotlin.math.roundToInt

internal object PickerTrackerHelper {
  /**
   * Based on a given [DeviceConfig] get the equivalent Tracking value for the device density.
   *
   * Note that the dpi is converted to one of the common Density buckets.
   */
  fun densityBucketOfDeviceConfig(config: DeviceConfig): PreviewPickerValue {
    val configCopy =
      config.toMutableConfig().apply {
        dimUnit = DimUnit.px
      } // We need pixel dimensions to calculate density
    val density =
      AvdScreenData.getScreenDensity(
        null,
        false,
        configCopy.dpi.toDouble(),
        configCopy.height.roundToInt()
      )
    return when (density) {
      Density.LOW -> PreviewPickerValue.DENSITY_LOW
      Density.MEDIUM -> PreviewPickerValue.DENSITY_MEDIUM
      Density.HIGH -> PreviewPickerValue.DENSITY_HIGH
      Density.XHIGH -> PreviewPickerValue.DENSITY_X_HIGH
      Density.XXHIGH -> PreviewPickerValue.DENSITY_XX_HIGH
      Density.XXXHIGH -> PreviewPickerValue.DENSITY_XXX_HIGH
      else -> {
        Logger.getInstance(this::class.java).warn("Unexpected density bucket: ${density.name}")
        PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE
      }
    }
  }
}
