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
package com.android.tools.idea.compose.preview.pickers.properties.enumsupport

import com.android.SdkConstants
import com.android.tools.idea.compose.preview.pickers.properties.ClassPsiCallParameter
import com.android.tools.idea.compose.preview.pickers.properties.PsiCallParameterPropertyItem
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue

/** Base interface for psi pickers, to support tracking assigned values. */
internal interface PsiEnumValue : EnumValue {
  val trackableValue: PreviewPickerValue

  override fun select(property: PropertyItem): Boolean =
    if (property is PsiCallParameterPropertyItem) {
      property.writeNewValue(value, false, trackableValue)
      true
    } else {
      super.select(property)
    }

  companion object {
    fun withTooltip(
      value: String,
      display: String,
      description: String?,
      trackingValue: PreviewPickerValue
    ) = DescriptionEnumValue(value, display, trackingValue, description)

    fun indented(value: String, display: String, trackingValue: PreviewPickerValue) =
      object : PsiEnumValueImpl(value = value, display = display, trackableValue = trackingValue) {
        override val indented: Boolean = true
      }
  }
}

/**
 * Base implementation of [PsiEnumValue], should aim to cover most use-cases found in [EnumValue].
 */
internal open class PsiEnumValueImpl(
  override val value: String?,
  override val display: String,
  override val trackableValue: PreviewPickerValue
) : PsiEnumValue

/**
 * Base interface that makes use of [ClassPsiCallParameter] functionality.
 *
 * Used to import classes and set parameter values that may use references to the imported class.
 */
internal interface BaseClassEnumValue : EnumValue {
  /** The fully qualified class that needs importing */
  val fqClass: String

  /** The new value String of the parameter */
  val valueToWrite: String

  /** Value to use in case the [fqClass] cannot be imported */
  val fqFallbackValue: String

  /**
   * Resolved primitive value for this [EnumValue], used for comparing with other references that
   * may lead to the same value
   */
  val resolvedValue: String

  /**
   * One of the supported tracking options that best represents the value assigned by this instance,
   * use [PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED] if there's no suitable option.
   */
  val trackableValue: PreviewPickerValue

  override val value: String?
    get() = resolvedValue

  override fun select(property: PropertyItem): Boolean {
    if (property is ClassPsiCallParameter) {
      property.importAndSetValue(fqClass, valueToWrite, fqFallbackValue, trackableValue)
    } else {
      property.value = fqFallbackValue
    }
    return true
  }
}

/**
 * [EnumValue] that sets the parameter value to a constant of an specific class. While importing the
 * needed class.
 *
 * E.g: For `MyClass.MY_CONSTANT`
 *
 * `import package.of.MyClass`
 *
 * `parameterName = MyClass.MY_CONSTANT`
 */
internal interface ClassConstantEnumValue : BaseClassEnumValue {
  val classConstant: String

  private val className: String
    get() = fqClass.substringAfterLast('.', fqClass)

  override val valueToWrite: String
    get() = "$className.$classConstant"

  override val fqFallbackValue: String
    get() = "$fqClass.$classConstant"
}

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

/** [PsiEnumValue] that includes a description, shown as a tooltip in [PsiEnumValueCellRenderer]. */
internal data class DescriptionEnumValue(
  override val value: String,
  override val display: String,
  override val trackableValue: PreviewPickerValue,
  val description: String?
) : PsiEnumValue {
  override val indented: Boolean = true
  override fun toString(): String = value
}
