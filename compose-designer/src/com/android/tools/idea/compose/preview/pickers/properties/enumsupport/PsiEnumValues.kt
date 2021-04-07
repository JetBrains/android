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
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem

/**
 * An [EnumValue] used to write values from an specific class.
 */
internal interface ClassEnumValue : EnumValue {
  val classValue: String

  val fqClass: String

  val resolvedValue: String

  private val className: String
    get() = fqClass.substringAfterLast('.', fqClass)

  override val value: String?
    get() = resolvedValue

  override fun select(property: PropertyItem): Boolean {
    if (property is ClassPsiCallParameter) {
      property.setFqValue(fqClass, className, classValue)
    }
    else {
      property.value = "$fqClass.$classValue"
    }
    return true
  }
}

internal interface UiModeEnumValue : ClassEnumValue {
  override val fqClass: String
    get() = SdkConstants.CLASS_CONFIGURATION
}

/**
 * Default implementation for the `uiMode` parameter. When selected, sets the property value as a reference of the selected Configuration
 * field.
 *
 * `uiMode = Configuration.UI_MODE_TYPE_NORMAL`
 *
 * @param classValue The simple value name that references a `uiMode` field in the Configuration class
 * @param display Display name seen in the dropdown menu
 * @param resolvedValue String of the actual value of the referenced field, used to know which option is currently selected
 */
internal class UiModeEnumValueImpl(
  override val classValue: String,
  override val display: String,
  override val resolvedValue: String
) : UiModeEnumValue

/**
 * A set of pre-defined [EnumValue]s for the `uiMode` parameter. Should only be used for reference/comparison or as fallback.
 */
internal enum class UiMode(
  override val classValue: String,
  override val display: String,
  override val resolvedValue: String
) : UiModeEnumValue {
  // TODO(154503873): Add proper support to display values as enums, currently, selecting one of these values, will leave the dropwdown
  //  empty, even though the value is properly set in the code.
  NORMAL("UI_MODE_TYPE_NORMAL", "Normal", "1") {
    override val value: String? = null
    override fun select(property: PropertyItem): Boolean {
      property.value = null
      return true
    }
  },
  DESK("UI_MODE_TYPE_DESK", "Desk", "2"),
  CAR("UI_MODE_TYPE_CAR", "Car", "3"),
  TELEVISION("UI_MODE_TYPE_TELEVISION", "Tv", "4"),
  APPLIANCE("UI_MODE_TYPE_APPLIANCE", "Appliance", "5"),
  WATCH("UI_MODE_TYPE_WATCH", "Watch", "6"),
  VR("UI_MODE_TYPE_VR_HEADSET", "Vr", "7");
}

internal interface DeviceEnumValue : ClassEnumValue {
  override val fqClass: String
    get() = "androidx.compose.ui.tooling.preview.Devices"
}

/**
 * Default implementation for the `device` parameter. When selected, sets the property value as a reference of the selected Device.
 *
 * `device = Devices.PIXEL`
 *
 * @param classValue The simple value name that references a `device` field in the Devices class
 * @param display Display name seen in the dropdown menu
 * @param resolvedValue String of the actual value of the referenced field, used to know which option is currently selected
 * @param fqClass The fully qualified Class name, used to property import the class into the file
 */
internal class DeviceEnumValueImpl(
  override val classValue: String,
  override val display: String,
  override val resolvedValue: String,
  override val fqClass: String
) : DeviceEnumValue

/**
 * A set of pre-defined [EnumValue]s for the `device` parameter. Should only be used for reference/comparison or as fallback.
 */
internal enum class Device(
  override val classValue: String,
  override val display: String,
  override val resolvedValue: String
) : DeviceEnumValue {
  DEFAULT("DEFAULT", "Default", "") {
    override val value: String? = null
    override fun select(property: PropertyItem): Boolean {
      property.value = null
      return true
    }
  },
  NEXUS_7("NEXUS_7", "Nexus 7 (2012)", "id:Nexus 7"),
  NEXUS_7_2013("NEXUS_7_2013", "Nexus 7", "id:Nexus 7 2013"),
  NEXUS_10("NEXUS_10", "Nexus 10", "name:Nexus 10"),
  PIXEL_C("PIXEL_C", "Pixel C", "id:pixel_c"),
  PIXEL_2("PIXEL_2", "Pixel 2", "id:pixel_2"),
  PIXEL_3("PIXEL_3", "Pixel 3", "id:pixel_3"),
  PIXEL_4("PIXEL_4", "Pixel 4", "id:pixel_4"),
  PIXEL_4_XL("PIXEL_4_XL", "Pixel 4 XL", "id:pixel_4_xl"),
  PIXEL_5("PIXEL_5", "Pixel 5", "id:pixel_5");
}

/**
 * Pre-defined Font scaling options, based from the options available in the Layout Validation tool window.
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