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
package com.android.tools.idea.compose.pickers.preview.tracking

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.compose.pickers.base.tracking.BaseComposePickerTracker
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
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EditorPickerEvent
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.DeviceType
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerParameter

/** Tracker implementation for the Preview picker. */
internal open class PreviewPickerTracker : BaseComposePickerTracker() {
  override fun doLogUsageData(actions: List<EditorPickerAction>) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.EDITOR_PICKER)
        .setEditorPickerEvent(EditorPickerEvent.newBuilder().addAllAction(actions))
    )
  }

  override fun convertModificationsToTrackerActions(
    modifications: List<PickerModification>
  ): List<EditorPickerAction> {
    return modifications.map { pickerModification ->
      val trackerParameter =
        when (pickerModification.propertyName) {
          PARAMETER_NAME -> PreviewPickerParameter.NAME
          PARAMETER_GROUP -> PreviewPickerParameter.GROUP
          PARAMETER_WIDTH_DP, PARAMETER_WIDTH -> PreviewPickerParameter.WIDTH
          PARAMETER_HEIGHT_DP, PARAMETER_HEIGHT -> PreviewPickerParameter.HEIGHT
          PARAMETER_API_LEVEL -> PreviewPickerParameter.API_LEVEL
          PARAMETER_FONT_SCALE -> PreviewPickerParameter.FONT_SCALE
          PARAMETER_SHOW_DECORATION, PARAMETER_SHOW_SYSTEM_UI ->
            PreviewPickerParameter.SHOW_SYSTEM_UI
          PARAMETER_SHOW_BACKGROUND -> PreviewPickerParameter.SHOW_BACKGROUND
          PARAMETER_BACKGROUND_COLOR -> PreviewPickerParameter.BACKGROUND_COLOR
          PARAMETER_UI_MODE -> PreviewPickerParameter.UI_MODE
          PARAMETER_LOCALE -> PreviewPickerParameter.LOCALE
          PARAMETER_DEVICE, PARAMETER_HARDWARE_DEVICE -> PreviewPickerParameter.DEVICE
          PARAMETER_HARDWARE_WIDTH -> PreviewPickerParameter.DEVICE_WIDTH
          PARAMETER_HARDWARE_HEIGHT -> PreviewPickerParameter.DEVICE_HEIGHT
          PARAMETER_HARDWARE_DIM_UNIT -> PreviewPickerParameter.DEVICE_DIM_UNIT
          PARAMETER_HARDWARE_DENSITY -> PreviewPickerParameter.DEVICE_DPI
          PARAMETER_HARDWARE_ORIENTATION -> PreviewPickerParameter.DEVICE_ORIENTATION
          else -> PreviewPickerParameter.UNKNOWN_PREVIEW_PICKER_PARAMETER
        }
      val deviceType = run {
        val device = pickerModification.deviceBeforeModification
        if (device?.id == Configuration.CUSTOM_DEVICE_ID) {
          return@run DeviceType.CUSTOM
        }
        // 'groupDevices' will assign our one device to a non-empty group, so we filter out any
        // group with empty device list
        val resultingGroup =
          groupDevices(listOfNotNull(device)).entries.firstNotNullOfOrNull { (group, devices) ->
            if (devices.isEmpty()) null else group
          }
        return@run when (resultingGroup) {
          DeviceGroup.NEXUS, DeviceGroup.NEXUS_XL -> DeviceType.PHONE
          DeviceGroup.NEXUS_TABLET -> DeviceType.TABLET
          DeviceGroup.WEAR -> DeviceType.WEAR
          DeviceGroup.DESKTOP -> DeviceType.DESKTOP
          DeviceGroup.TV -> DeviceType.TV
          DeviceGroup.AUTOMOTIVE ->
            DeviceType.UNKNOWN_DEVICE_TYPE // TODO(b/205184728): Add tracker value for Auto
          DeviceGroup.GENERIC -> DeviceType.GENERIC
          DeviceGroup.OTHER, // unused in picker
          null -> DeviceType.UNKNOWN_DEVICE_TYPE
        }
      }

      EditorPickerAction.newBuilder()
        .setPreviewModification(
          with(PreviewPickerModification.newBuilder()) {
            parameter = trackerParameter
            closestDeviceType = deviceType
            assignedValue = pickerModification.assignedValue
            build()
          }
        )
        .build()
    }
  }
}
