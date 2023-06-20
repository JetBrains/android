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
package com.android.tools.idea.compose.pickers.preview.enumsupport

import com.android.SdkConstants
import com.android.tools.compose.COMPOSE_UI_TOOLING_PREVIEW_PACKAGE
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.ReferenceDesktopConfig
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.ReferenceFoldableConfig
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.ReferencePhoneConfig
import com.android.tools.idea.compose.pickers.preview.enumsupport.devices.ReferenceTabletConfig
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.util.androidFacet
import com.android.tools.property.panel.api.HeaderEnumValue
import com.intellij.openapi.module.Module
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubConfigurationAsLibrary
import org.jetbrains.android.compose.stubDevicesAsLibrary
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Language("XML")
private const val STRINGS_CONTENT =
  """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">my app</string>
</resources>
"""

@RunWith(Parameterized::class)
class PreviewPickerValuesProviderTest(previewAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview")
    val namespaces = namespaceVariations.map { it[0] }.distinct()
  }

  private val PREVIEW_TOOLING_PACKAGE = previewAnnotationPackage

  @get:Rule val rule = AndroidProjectRule.inMemory()

  @get:Rule val edtRule = EdtRule()

  private val module: Module
    get() = rule.fixture.module

  @Before
  fun setup() {
    ConfigurationManager.getOrCreateInstance(module)
    rule.fixture.stubDevicesAsLibrary(COMPOSE_UI_TOOLING_PREVIEW_PACKAGE)
  }

  @RunsInEdt
  @Test
  fun testValuesProvider() {
    rule.fixture.stubConfigurationAsLibrary()

    val manifest =
      rule.fixture.addFileToProjectAndInvalidate(SdkConstants.FN_ANDROID_MANIFEST_XML, "")
    rule.fixture.addFileToProjectAndInvalidate(
      "res/values/strings.xml",
      STRINGS_CONTENT
    ) // Should not affect locale options
    rule.fixture.addFileToProjectAndInvalidate("res/values-es-rES/strings.xml", STRINGS_CONTENT)
    rule.fixture.addFileToProjectAndInvalidate("res/values-en-rUS/strings.xml", STRINGS_CONTENT)
    rule.fixture.addFileToProjectAndInvalidate("res/values-en-rGB/strings.xml", STRINGS_CONTENT)
    SourceProviderManager.replaceForTest(
      module.androidFacet!!,
      rule.fixture.projectDisposable,
      NamedIdeaSourceProviderBuilder.create("main", manifest.virtualFile.url)
        .withResDirectoryUrls(listOf(rule.fixture.findFileInTempDir("res").url))
        .build()
    )

    val valuesProvider = PreviewPickerValuesProvider.createPreviewValuesProvider(module, null)
    val uiModeValues = valuesProvider.getValuesProvider("uiMode")!!.invoke()
    assertEquals(10, uiModeValues.size)
    // The Normal mode should go right after the header, remaining options are sorted on their
    // resolved value
    assertEquals("Not Night", (uiModeValues[0] as HeaderEnumValue).header)
    assertEquals("Normal", uiModeValues[1].display)
    assertEquals("Undefined", uiModeValues[2].display)
    assertEquals("Desk", uiModeValues[3].display)
    assertEquals("Car", uiModeValues[4].display)
    assertEquals("Night", (uiModeValues[5] as HeaderEnumValue).header)
    assertEquals("Normal", uiModeValues[6].display)

    val deviceValues = valuesProvider.getValuesProvider("Device")!!.invoke()
    assertEquals(
      18,
      deviceValues.size
    ) // 4 headers + 3 separators + 11 devices (4 Reference, 3 Wear, 3 TV, 1 Auto)
    // Generic devices are not shown since they are empty when running on test
    assertEquals("Reference Devices", (deviceValues[0] as HeaderEnumValue).header)
    assertEquals("Medium Phone", deviceValues[1].display)
    assertEquals("Foldable", deviceValues[2].display)
    assertEquals("Medium Tablet", deviceValues[3].display)
    assertEquals("Desktop", deviceValues[4].display)
    assertEquals("Wear", (deviceValues[6] as HeaderEnumValue).header)
    assertEquals("Tv", (deviceValues[11] as HeaderEnumValue).header)
    assertEquals("Auto", (deviceValues[16] as HeaderEnumValue).header)

    // Verify reference values
    assertEquals(ReferencePhoneConfig.deviceSpec(), deviceValues[1].value)
    assertEquals(ReferenceFoldableConfig.deviceSpec(), deviceValues[2].value)
    assertEquals(ReferenceTabletConfig.deviceSpec(), deviceValues[3].value)
    assertEquals(ReferenceDesktopConfig.deviceSpec(), deviceValues[4].value)

    // Verify Wear, Tv and Auto are custom devices (start with "spec:")
    assertTrue(deviceValues[7].value!!.startsWith("spec:"))
    assertTrue(deviceValues[8].value!!.startsWith("spec:"))
    assertTrue(deviceValues[9].value!!.startsWith("spec:"))
    assertTrue(deviceValues[12].value!!.startsWith("spec:"))
    assertTrue(deviceValues[13].value!!.startsWith("spec:"))
    assertTrue(deviceValues[14].value!!.startsWith("spec:"))
    assertTrue(deviceValues[17].value!!.startsWith("spec:"))

    val localeValues = valuesProvider.getValuesProvider("locale")!!.invoke()
    assertEquals(4, localeValues.size)
    assertEquals("Default (en-US)", localeValues[0].display)
    assertEquals(null, localeValues[0].value)
    assertEquals("en-GB", localeValues[1].display)
    assertEquals("en-rGB", localeValues[1].value)
    assertEquals("en-US", localeValues[2].display)
    assertEquals("en-rUS", localeValues[2].value)
    assertEquals("es-ES", localeValues[3].display)
    assertEquals("es-rES", localeValues[3].value)

    val wallpaperValues = valuesProvider.getValuesProvider("wallpaper")!!.invoke()
    assertEquals(5, wallpaperValues.size)
    assertEquals("None", wallpaperValues[0].display)
    assertEquals("Red dominated", wallpaperValues[1].display)
    assertEquals("Green dominated", wallpaperValues[2].display)
    assertEquals("Blue dominated", wallpaperValues[3].display)
    assertEquals("Yellow dominated", wallpaperValues[4].display)
  }

  @RunsInEdt
  @Test
  fun testValuesProviderWithSdk() {
    Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, module)

    val valuesProvider = PreviewPickerValuesProvider.createPreviewValuesProvider(module, null)
    val uiModeValues = valuesProvider.getValuesProvider("uiMode")!!.invoke()
    assertEquals(18, uiModeValues.size)
    // We only care of the order of the first 2 options, all else are sorted on their value and
    // their availability depends on the sdk used
    assertEquals("Not Night", (uiModeValues[0] as HeaderEnumValue).header)
    assertEquals("Normal", uiModeValues[1].display) // Preferred first option
    assertEquals("Undefined", uiModeValues[2].display) // Has lowest value (0)

    // Find 'Night' separator
    var nightModeIndex = uiModeValues.indexOfLast { it is HeaderEnumValue }
    assertEquals("Night", (uiModeValues[nightModeIndex] as HeaderEnumValue).header)
    assertEquals("Normal", uiModeValues[++nightModeIndex].display)
    assertEquals("Undefined", uiModeValues[++nightModeIndex].display)

    val deviceEnumValues = valuesProvider.getValuesProvider("Device")!!.invoke()
    val deviceHeaders = deviceEnumValues.filterIsInstance<HeaderEnumValue>()
    // With Sdk we just check that each Device category exists, meaning that they are populated
    assertEquals("Reference Devices", deviceHeaders[0].header)
    assertEquals("Phone", deviceHeaders[1].header)
    assertEquals("Tablet", deviceHeaders[2].header)
    assertEquals("Desktop", deviceHeaders[3].header)
    assertEquals("Wear", deviceHeaders[4].header)
    assertEquals("Tv", deviceHeaders[5].header)
    assertEquals("Auto", deviceHeaders[6].header)
    assertEquals("Generic Devices", deviceHeaders[7].header)

    // With Sdk verify that Wear, Tv and Auto have actual devices (their value start with "id:"
    // instead of "spec:")
    val wearIndex = deviceEnumValues.indexOfFirst { it is HeaderEnumValue && it.header == "Wear" }
    assertTrue(deviceEnumValues[wearIndex + 1].value!!.startsWith("id:"))

    val tvIndex = deviceEnumValues.indexOfFirst { it is HeaderEnumValue && it.header == "Tv" }
    assertTrue(deviceEnumValues[tvIndex + 1].value!!.startsWith("id:"))

    val autoIndex = deviceEnumValues.indexOfFirst { it is HeaderEnumValue && it.header == "Auto" }
    assertTrue(deviceEnumValues[autoIndex + 1].value!!.startsWith("id:"))

    // For api, we just check that there's at least an element available
    val apiLevelValues = valuesProvider.getValuesProvider("apiLevel")!!.invoke()
    assertTrue(apiLevelValues.isNotEmpty())
  }

  @RunsInEdt
  @Test
  fun testGroupValuesProvider() {
    rule.fixture
      .stubComposableAnnotation() // Package does not matter, we are not testing the Composable
    // annotation
    rule.fixture.stubPreviewAnnotation(COMPOSE_UI_TOOLING_PREVIEW_PACKAGE)
    val file =
      rule.fixture.addFileToProjectAndInvalidate(
        "Test.kt",
        // language=kotlin
        """
        import androidx.compose.Composable
        import $PREVIEW_TOOLING_PACKAGE.Preview

        @Preview
        @Composable
        fun preview1() {}

        @Composable
        @Preview(group = "group1")
        fun preview2() {}

        @Preview(group = "group2")
        @Composable
        fun preview3() {}
      """
          .trimIndent()
      )

    val valuesProvider =
      PreviewPickerValuesProvider.createPreviewValuesProvider(module, file.virtualFile)
    val groupEnumValues = valuesProvider.getValuesProvider("group")!!.invoke()
    assertEquals(2, groupEnumValues.size)
    assertEquals("group1", groupEnumValues[0].display)
    assertEquals("group2", groupEnumValues[1].display)
  }
}
