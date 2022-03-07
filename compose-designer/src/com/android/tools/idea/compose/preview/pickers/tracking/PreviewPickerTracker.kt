/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.tracking

import com.android.tools.idea.compose.preview.PARAMETER_API_LEVEL
import com.android.tools.idea.compose.preview.PARAMETER_BACKGROUND_COLOR
import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_FONT_SCALE
import com.android.tools.idea.compose.preview.PARAMETER_GROUP
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DENSITY
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_WIDTH
import com.android.tools.idea.compose.preview.PARAMETER_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_HEIGHT_DP
import com.android.tools.idea.compose.preview.PARAMETER_LOCALE
import com.android.tools.idea.compose.preview.PARAMETER_NAME
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_BACKGROUND
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_DECORATION
import com.android.tools.idea.compose.preview.PARAMETER_SHOW_SYSTEM_UI
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.compose.preview.PARAMETER_WIDTH
import com.android.tools.idea.compose.preview.PARAMETER_WIDTH_DP
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.DeviceGroup
import com.android.tools.idea.configurations.groupDevices

/**
 * Tracker implementation for the Preview picker.
 */
internal open class PreviewPickerTracker: BaseComposePickerTracker() {
  override fun doLogUsageData(actions: List<PickerAction>) {
    val pickerEvent = PickerEvent(type = PickerType.PREVIEW, actions)
    // TODO(205184728): Finish implementation once the studio_stats object has been updated with our tracking classes
  }

  override fun convertModificationsToTrackerActions(modifications: List<PickerModification>): List<PickerAction> {
    return modifications.map { pickerModification ->
      val trackerParameter = when (pickerModification.propertyName) {
        PARAMETER_NAME -> PreviewParameter.NAME
        PARAMETER_GROUP -> PreviewParameter.GROUP
        PARAMETER_WIDTH_DP,
        PARAMETER_WIDTH -> PreviewParameter.WIDTH
        PARAMETER_HEIGHT_DP,
        PARAMETER_HEIGHT -> PreviewParameter.HEIGHT
        PARAMETER_API_LEVEL -> PreviewParameter.API_LEVEL
        PARAMETER_FONT_SCALE -> PreviewParameter.FONT_SCALE
        PARAMETER_SHOW_DECORATION,
        PARAMETER_SHOW_SYSTEM_UI -> PreviewParameter.SHOW_SYSTEM_UI
        PARAMETER_SHOW_BACKGROUND -> PreviewParameter.SHOW_BACKGROUND
        PARAMETER_BACKGROUND_COLOR -> PreviewParameter.BACKGROUND_COLOR
        PARAMETER_UI_MODE -> PreviewParameter.UI_MODE
        PARAMETER_LOCALE -> PreviewParameter.LOCALE
        PARAMETER_DEVICE,
        PARAMETER_HARDWARE_DEVICE -> PreviewParameter.DEVICE
        PARAMETER_HARDWARE_WIDTH -> PreviewParameter.DEVICE_WIDTH
        PARAMETER_HARDWARE_HEIGHT -> PreviewParameter.DEVICE_HEIGHT
        PARAMETER_HARDWARE_DIM_UNIT -> PreviewParameter.DEVICE_DIM_UNIT
        PARAMETER_HARDWARE_DENSITY -> PreviewParameter.DEVICE_DENSITY
        PARAMETER_HARDWARE_ORIENTATION -> PreviewParameter.DEVICE_ORIENTATION
        else -> PreviewParameter.UNKNOWN
      }
      val deviceType = run {
        val device = pickerModification.deviceBeforeModification
        if (device?.id == Configuration.CUSTOM_DEVICE_ID) {
          return@run DeviceType.Custom
        }
        // 'groupDevices' will assign our one device to a non-empty group, so we filter out any group with empty device list
        val resultingGroup = groupDevices(listOfNotNull(device)).entries.firstNotNullOfOrNull { (group, devices) ->
          if (devices.isEmpty()) null else group
        }
        return@run when (resultingGroup) {
          DeviceGroup.NEXUS,
          DeviceGroup.NEXUS_XL -> DeviceType.Phone
          DeviceGroup.NEXUS_TABLET -> DeviceType.Tablet
          DeviceGroup.WEAR -> DeviceType.Wear
          DeviceGroup.TV -> DeviceType.Tv
          DeviceGroup.AUTOMOTIVE -> DeviceType.Auto
          DeviceGroup.GENERIC -> DeviceType.Generic
          DeviceGroup.OTHER, // unused in picker
          null -> DeviceType.Unknown
        }
      }

      PickerAction(
        previewPickerModification = PreviewPickerModification(
          trackerParameter,
          deviceType,
          pickerModification.assignedValue
        )
      )
    }
  }
}