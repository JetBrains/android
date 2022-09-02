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

import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyItem
import com.android.tools.property.panel.api.EnumValue
import kotlin.test.assertEquals
import org.junit.Test

class PsiEnumProviderTest {

  val psiEnumProvider = PsiEnumProvider(EnumSupportValuesProvider.EMPTY)

  @Test
  fun testDefaultUiModeEnumValues() {
    // Night mode mask support
    checkDefaultEnumValue(0x11.toString(), "Normal", getUiModeDefaultEnumValue(0x11.toString()))
    checkDefaultEnumValue(0x21.toString(), "Normal", getUiModeDefaultEnumValue(0x21.toString()))

    // Supported types (undefined night mode)
    checkDefaultEnumValue("0", "Undefined", getUiModeDefaultEnumValue("0"))
    checkDefaultEnumValue("1", "Normal", getUiModeDefaultEnumValue("1"))
    checkDefaultEnumValue("2", "Desk", getUiModeDefaultEnumValue("2"))
    checkDefaultEnumValue("3", "Car", getUiModeDefaultEnumValue("3"))
    checkDefaultEnumValue("4", "Tv", getUiModeDefaultEnumValue("4"))
    checkDefaultEnumValue("5", "Appliance", getUiModeDefaultEnumValue("5"))
    checkDefaultEnumValue("6", "Watch", getUiModeDefaultEnumValue("6"))
    checkDefaultEnumValue("7", "Vr", getUiModeDefaultEnumValue("7"))

    // Unsupported, value is kept to avoid unintended modifications
    checkDefaultEnumValue("", "", getUiModeDefaultEnumValue(""))
    checkDefaultEnumValue("hello", "hello", getUiModeDefaultEnumValue("hello"))
    checkDefaultEnumValue("8", "Unknown", getUiModeDefaultEnumValue("8"))
    checkDefaultEnumValue(0x41.toString(), "Unknown", getUiModeDefaultEnumValue(0x41.toString()))
  }

  @Test
  fun testDefaultDeviceEnumValues() {
    // Default equivalent
    checkDefaultEnumValue("", "Default", getDeviceDefaultEnumValue(""))
    checkDefaultEnumValue("", "Default", getDeviceDefaultEnumValue("   "))

    // Some pre-defined devices
    checkDefaultEnumValue(
      "id:Nexus 7 2013",
      "Nexus 7 (2013)",
      getDeviceDefaultEnumValue("id:Nexus 7 2013")
    )
    checkDefaultEnumValue("name:Nexus 10", "Nexus 10", getDeviceDefaultEnumValue("name:Nexus 10"))
    checkDefaultEnumValue("id:pixel_4_xl", "Pixel 4 XL", getDeviceDefaultEnumValue("id:pixel_4_xl"))

    // Parsed devices
    checkDefaultEnumValue("id:my device", "my device", getDeviceDefaultEnumValue("id:my device"))
    checkDefaultEnumValue("id:pixel_fake", "pixel fake", getDeviceDefaultEnumValue("id:pixel_fake"))
    checkDefaultEnumValue(
      "name:Pixel Fake",
      "Pixel Fake",
      getDeviceDefaultEnumValue("name:Pixel Fake")
    )

    // Device spec
    checkDefaultEnumValue(
      "spec:Normal;100;200;px;140dpi",
      "Custom",
      getDeviceDefaultEnumValue("spec:Normal;100;200;px;140dpi")
    )

    // Density should return a density on a specific bucket
    checkDefaultEnumValue("160", "mdpi (160 dpi)", getDensityDefaultEnumValue("199"))
    checkDefaultEnumValue("240", "hdpi (240 dpi)", getDensityDefaultEnumValue("200"))
    checkDefaultEnumValue("240", "hdpi (240 dpi)", getDensityDefaultEnumValue("201"))

    // Unsupported values
    checkDefaultEnumValue("my device", "my device", getDeviceDefaultEnumValue("my device"))
  }

  private fun getUiModeDefaultEnumValue(initialValue: String) =
    psiEnumProvider(FakePsiProperty("uiMode"))!!.createValue(initialValue)

  private fun getDeviceDefaultEnumValue(initialValue: String) =
    psiEnumProvider(FakePsiProperty("Device"))!!.createValue(initialValue)

  private fun getDensityDefaultEnumValue(initialValue: String) =
    psiEnumProvider(FakePsiProperty("Density"))!!.createValue(initialValue)
}

private fun checkDefaultEnumValue(
  expectedValue: String,
  expectedDisplay: String,
  enumValue: EnumValue
) {
  assertEquals(expectedValue, enumValue.value)
  assertEquals(expectedDisplay, enumValue.display)
}

private class FakePsiProperty(
  override var name: String,
) : PsiPropertyItem {
  override var value: String? = null
}
