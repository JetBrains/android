/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.PropertyItem

/**
 * An [EnumValue] used to write values from an specific class.
 */
internal interface ClassEnumValue : EnumValue {
  val classValue: String

  val fqClass: String

  private val className: String
    get() = fqClass.substringAfterLast('.', fqClass)

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

// TODO(154503873): Add remaining supported UiModes, or update so that the values are pulled from the project's Sdk.
internal enum class UiMode(override val classValue: String, override val display: String, val resolvedValue: Int) : ClassEnumValue {
  // TODO(154503873): Add proper support to display values as enums, currently, selecting one of these values, will leave the dropwdown
  //  empty, even though the value is properly set in the code.
  NORMAL("UI_MODE_TYPE_NORMAL", "Normal", 1) {
    override val value: String? = null
    override fun select(property: PropertyItem): Boolean {
      property.value = null
      return true
    }
  },
  DESK("UI_MODE_TYPE_DESK", "Desk", 2),
  CAR("UI_MODE_TYPE_CAR", "Car", 3),
  TELEVISION("UI_MODE_TYPE_TELEVISION", "Tv", 4),
  APPLIANCE("UI_MODE_TYPE_APPLIANCE", "Appliance", 5),
  WATCH("UI_MODE_TYPE_WATCH", "Watch", 6),
  VR("UI_MODE_TYPE_VR_HEADSET", "Vr", 7);

  override val fqClass: String = "android.content.res.Configuration"

  override val value: String? = resolvedValue.toString()
}

// TODO(154503873): Add remaining supported Devices, or update so that the values are pulled from the Devices class in the project.
internal enum class Device(override val classValue: String, override val display: String, val resolvedValue: String) : ClassEnumValue {
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

  override val fqClass: String = "androidx.compose.ui.tooling.preview.Devices"

  override val value: String? = resolvedValue
}

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