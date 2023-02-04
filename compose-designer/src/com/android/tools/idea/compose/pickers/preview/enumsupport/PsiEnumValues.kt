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
package com.android.tools.idea.compose.pickers.preview.enumsupport

import com.android.SdkConstants
import com.android.tools.idea.compose.pickers.common.enumsupport.BaseClassEnumValue
import com.android.tools.idea.compose.pickers.common.enumsupport.ClassConstantEnumValue
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue

/**
 * Mask for the corresponding bits of possible TYPES in the ui mode parameter. I.e: When applied,
 * clears the night mode bits
 */
internal const val UI_MODE_TYPE_MASK = 0x0F

/**
 * Mask for the corresponding bits of possible NIGHT values in the ui mode parameter. I.e: When
 * applied, clears the types bits
 */
internal const val UI_MODE_NIGHT_MASK = 0x30

/**
 * Implementation for the 'uiMode' parameter that applies a 'night' flag when the value is selected.
 *
 * E.g: For `UI_MODE_TYPE_NORMAL` in night mode
 *
 * `uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL`
 *
 * @param isNight When true, `UI_MODE_NIGHT_YES` is used, `UI_MODE_NIGHT_NO` for false
 * @param uiModeType The specific ui mode being used, identified by the `TYPE` prefix, e.g:
 * `UI_MODE_TYPE_NORMAL`
 * @param display Display name seen in the dropdown menu
 * @param uiModeTypeResolvedValue String of the actual value of the referenced field, used to know
 * which option is currently selected, will be mixed with the resolved value of the selected night
 * mode
 */
internal class UiModeWithNightMaskEnumValue(
  isNight: Boolean,
  uiModeType: String,
  override val display: String,
  uiModeTypeResolvedValue: String
) : BaseClassEnumValue {

  private val nightModeString = if (isNight) "UI_MODE_NIGHT_YES" else "UI_MODE_NIGHT_NO"

  override val valueToWrite: String = "Configuration.$nightModeString or Configuration.$uiModeType"

  override val fqFallbackValue: String =
    "${SdkConstants.CLASS_CONFIGURATION}.$nightModeString or ${SdkConstants.CLASS_CONFIGURATION}.$uiModeType}"

  override val fqClass: String = SdkConstants.CLASS_CONFIGURATION

  override val resolvedValue: String =
    kotlin.run {
      val nightModeValue = if (isNight) 0x20 else 0x10
      return@run ((uiModeTypeResolvedValue.toIntOrNull() ?: 0) or nightModeValue).toString()
    }

  override val trackableValue: PreviewPickerValue =
    if (isNight) PreviewPickerValue.UI_MODE_NIGHT else PreviewPickerValue.UI_MODE_NOT_NIGHT

  override val indented: Boolean = true

  companion object {
    /**
     * Create an [EnumValue] for the UiMode: [uiModeType], with the `UI_MODE_NIGHT_NO` mask applied.
     */
    fun createNotNightUiModeEnumValue(
      uiModeType: String,
      display: String,
      uiModeTypeResolvedValue: String
    ) = UiModeWithNightMaskEnumValue(false, uiModeType, display, uiModeTypeResolvedValue)

    /**
     * Create an [EnumValue] for the UiMode: [uiModeType], with the `UI_MODE_NIGHT_YES` mask
     * applied.
     */
    fun createNightUiModeEnumValue(
      uiModeType: String,
      display: String,
      uiModeTypeResolvedValue: String
    ) = UiModeWithNightMaskEnumValue(true, uiModeType, display, uiModeTypeResolvedValue)

    /** Pre-defined [EnumValue] for `UI_MODE_TYPE_NORMAL` in not night mode (`UI_MODE_NIGHT_NO`) */
    val NormalNotNightEnumValue =
      UiModeWithNightMaskEnumValue(
        false,
        UiMode.NORMAL.classConstant,
        UiMode.NORMAL.display,
        UiMode.NORMAL.resolvedValue
      )

    /** Pre-defined [EnumValue] for `UI_MODE_TYPE_NORMAL` in night mode (`UI_MODE_NIGHT_YES`) */
    val NormalNightEnumValue =
      UiModeWithNightMaskEnumValue(
        true,
        UiMode.NORMAL.classConstant,
        UiMode.NORMAL.display,
        UiMode.NORMAL.resolvedValue
      )
  }
}

/**
 * A set of pre-defined [EnumValue]s for the `uiMode` parameter. Should only be used for
 * reference/comparison or as fallback.
 */
internal enum class UiMode(
  override val classConstant: String,
  override val display: String,
  override val resolvedValue: String
) : ClassConstantEnumValue {
  // TODO(154503873): Add proper support to display values as enums, currently, selecting one of
  // these values, will leave the dropwdown
  //  empty, even though the value is properly set in the code.
  UNDEFINED("UI_MODE_TYPE_UNDEFINED", "Undefined", "0"),
  NORMAL("UI_MODE_TYPE_NORMAL", "Normal", "1"),
  DESK("UI_MODE_TYPE_DESK", "Desk", "2"),
  CAR("UI_MODE_TYPE_CAR", "Car", "3"),
  TELEVISION("UI_MODE_TYPE_TELEVISION", "Tv", "4"),
  APPLIANCE("UI_MODE_TYPE_APPLIANCE", "Appliance", "5"),
  WATCH("UI_MODE_TYPE_WATCH", "Watch", "6"),
  VR("UI_MODE_TYPE_VR_HEADSET", "Vr", "7");

  override val fqClass: String = SdkConstants.CLASS_CONFIGURATION
  override val trackableValue: PreviewPickerValue = PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED
}

/**
 * A set of pre-defined [EnumValue]s for the `device` parameter. Should only be used for
 * reference/comparison or as fallback.
 */
internal enum class Device(
  override val classConstant: String,
  override val display: String,
  override val resolvedValue: String
) : ClassConstantEnumValue {
  DEFAULT("DEFAULT", "Default", ""),
  NEXUS_7("NEXUS_7", "Nexus 7", "id:Nexus 7"),
  NEXUS_7_2013("NEXUS_7_2013", "Nexus 7 (2013)", "id:Nexus 7 2013"),
  NEXUS_10("NEXUS_10", "Nexus 10", "name:Nexus 10"),
  PIXEL_C("PIXEL_C", "Pixel C", "id:pixel_c"),
  PIXEL_2("PIXEL_2", "Pixel 2", "id:pixel_2"),
  PIXEL_3("PIXEL_3", "Pixel 3", "id:pixel_3"),
  PIXEL_4("PIXEL_4", "Pixel 4", "id:pixel_4"),
  PIXEL_4_XL("PIXEL_4_XL", "Pixel 4 XL", "id:pixel_4_xl"),
  PIXEL_5("PIXEL_5", "Pixel 5", "id:pixel_5");

  override val fqClass: String =
    "androidx.compose.ui.tooling.preview.Devices" // We assume this class for pre-defined Devices
  override val trackableValue: PreviewPickerValue = PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED
}

/**
 * Pre-defined Font scaling options, based from the options available in the Layout Validation tool
 * window.
 */
internal enum class FontScale(scaleValue: Float, visibleName: String) : EnumValue {
  DEFAULT(1f, "Default (100%)"),
  SMALL(0.85f, "Small (85%)"),
  LARGE(1.15f, "Large (115%)"),
  LARGEST(1.30f, "Largest (130%)");

  override val value: String = "%.2f".format(scaleValue)

  override val display: String = visibleName

  override fun select(property: PropertyItem): Boolean {
    property.value = "${this.value}f"
    return true
  }
}

/**
 * Predefined options for Wallpaper settings that correspond to what is available from
 * androidx.compose.ui.tooling.preview.Wallpapers.
 */
internal enum class Wallpaper(
  override val classConstant: String,
  override val display: String,
  override val resolvedValue: String
) : ClassConstantEnumValue {
  NONE("NONE", "None", "-1"),
  RED("RED_DOMINATED_EXAMPLE", "Red dominated", "0"),
  GREEN("GREEN_DOMINATED_EXAMPLE", "Green dominated", "1"),
  BLUE("BLUE_DOMINATED_EXAMPLE", "Blue dominated", "2"),
  YELLOW("YELLOW_DOMINATED_EXAMPLE", "Yellow dominated", "3");

  override val fqClass: String = "androidx.compose.ui.tooling.preview.Wallpapers"
  override val trackableValue: PreviewPickerValue = PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED
}
