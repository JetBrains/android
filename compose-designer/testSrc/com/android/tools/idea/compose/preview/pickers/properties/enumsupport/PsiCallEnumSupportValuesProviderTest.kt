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
import com.android.tools.compose.ComposeLibraryNamespace
import com.android.tools.idea.compose.preview.addFileToProjectAndInvalidate
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.util.androidFacet
import com.android.tools.property.panel.api.HeaderEnumValue
import com.intellij.openapi.module.Module
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubConfigurationAsLibrary
import org.jetbrains.android.compose.stubDevicesAsLibrary
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.jetbrains.android.facet.SourceProviderManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Language("XML")
private const val STRINGS_CONTENT = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">my app</string>
</resources>
"""

@RunWith(Parameterized::class)
class PsiCallEnumSupportValuesProviderTest(previewAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview")
    val namespaces = namespaceVariations.map { it[0] }.distinct()
  }

  private val PREVIEW_TOOLING_PACKAGE = previewAnnotationPackage

  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()

  private val composeLibraryNamespace = ComposeLibraryNamespace.values().first { it.previewPackage == previewAnnotationPackage }
  private val module: Module
    get() = rule.fixture.module

  @Before
  fun setup() {
    ConfigurationManager.getOrCreateInstance(module)
    rule.fixture.stubDevicesAsLibrary(composeLibraryNamespace.previewPackage)
  }

  @RunsInEdt
  @Test
  fun testValuesProvider() {
    rule.fixture.stubConfigurationAsLibrary()

    val manifest = rule.fixture.addFileToProjectAndInvalidate(SdkConstants.FN_ANDROID_MANIFEST_XML, "")
    rule.fixture.addFileToProjectAndInvalidate("res/values/strings.xml", STRINGS_CONTENT) // Should not affect locale options
    rule.fixture.addFileToProjectAndInvalidate("res/values-es-rES/strings.xml", STRINGS_CONTENT)
    rule.fixture.addFileToProjectAndInvalidate("res/values-en-rUS/strings.xml", STRINGS_CONTENT)
    rule.fixture.addFileToProjectAndInvalidate("res/values-en-rGB/strings.xml", STRINGS_CONTENT)
    SourceProviderManager.replaceForTest(
      module.androidFacet!!,
      rule.fixture.projectDisposable,
      NamedIdeaSourceProviderBuilder.create(
        "main", manifest.virtualFile.url
      ).withResDirectoryUrls(listOf(rule.fixture.findFileInTempDir("res").url)).build()
    )

    val valuesProvider = PsiCallEnumSupportValuesProvider.createPreviewValuesProvider(module, composeLibraryNamespace, null)
    val uiModeValues = valuesProvider.getValuesProvider("uiMode")!!.invoke()
    assertEquals(10, uiModeValues.size)
    // The Normal mode should go right after the header, remaining options are sorted on their resolved value
    assertEquals("Not Night", (uiModeValues[0] as HeaderEnumValue).header)
    assertEquals("Normal", uiModeValues[1].display)
    assertEquals("Undefined", uiModeValues[2].display)
    assertEquals("Desk", uiModeValues[3].display)
    assertEquals("Car", uiModeValues[4].display)
    assertEquals("Night", (uiModeValues[5] as HeaderEnumValue).header)
    assertEquals("Normal", uiModeValues[6].display)

    val deviceValues = valuesProvider.getValuesProvider("device")!!.invoke()
    assertEquals(7, deviceValues.size)
    // For Library devices order is: Default, Pixel family, Nexus family, everything else
    assertEquals("Library", (deviceValues[0] as HeaderEnumValue).header)
    assertEquals("Default", deviceValues[1].display)
    assertEquals("Pixel ", deviceValues[2].display)
    assertEquals("Pixel 4", deviceValues[3].display)
    assertEquals("Nexus 10", deviceValues[4].display)
    assertEquals("Nexus 7", deviceValues[5].display)
    assertEquals("Automotive 1024p", deviceValues[6].display)

    val localeValues = valuesProvider.getValuesProvider("locale")!!.invoke()
    assertEquals(3, localeValues.size)
    assertEquals("en-GB", localeValues[0].display)
    assertEquals("en-rGB", localeValues[0].value)
    assertEquals("en-US", localeValues[1].display)
    assertEquals("en-rUS", localeValues[1].value)
    assertEquals("es-ES", localeValues[2].display)
    assertEquals("es-rES", localeValues[2].value)
  }

  @RunsInEdt
  @Test
  fun testValuesProviderWithSdk() {
    Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, module)

    val valuesProvider = PsiCallEnumSupportValuesProvider.createPreviewValuesProvider(module, composeLibraryNamespace, null)
    val uiModeValues = valuesProvider.getValuesProvider("uiMode")!!.invoke()
    assertEquals(18, uiModeValues.size)
    // We only care of the order of the first 2 options, all else are sorted on their value and their availability depends on the sdk used
    assertEquals("Not Night", (uiModeValues[0] as HeaderEnumValue).header)
    assertEquals("Normal", uiModeValues[1].display) // Preferred first option
    assertEquals("Undefined", uiModeValues[2].display) // Has lowest value (0)

    // Find 'Night' separator
    var nightModeIndex = uiModeValues.indexOfLast { it is HeaderEnumValue }
    assertEquals("Night", (uiModeValues[nightModeIndex] as HeaderEnumValue).header)
    assertEquals("Normal", uiModeValues[++nightModeIndex].display)
    assertEquals("Undefined", uiModeValues[++nightModeIndex].display)

    val deviceValues = valuesProvider.getValuesProvider("device")!!.invoke()
    // With Sdk we just check that there's a Device Manager separator, the Library options are constant from the test setup
    assertEquals("Library", (deviceValues[0] as HeaderEnumValue).header)
    assertEquals("Device Manager", (deviceValues[7] as HeaderEnumValue).header)
    assertTrue(deviceValues.size > 8)
    // Can't control what Devices may show up here, but we can at least validate part of the EnumValue
    assertTrue(deviceValues[8].value!!.startsWith("id:"))

    // For api, we just check that there's at least an element available
    val apiLevelValues = valuesProvider.getValuesProvider("apiLevel")!!.invoke()
    assertTrue(apiLevelValues.isNotEmpty())
  }

  @RunsInEdt
  @Test
  fun testGroupValuesProvider() {
    rule.fixture.stubComposableAnnotation() // Package does not matter, we are not testing the Composable annotation
    rule.fixture.stubPreviewAnnotation(composeLibraryNamespace.previewPackage)
    val file = rule.fixture.addFileToProjectAndInvalidate(
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
      """.trimIndent())

    val valuesProvider = PsiCallEnumSupportValuesProvider.createPreviewValuesProvider(module, composeLibraryNamespace, file.virtualFile)
    val groupEnumValues = valuesProvider.getValuesProvider("group")!!.invoke()
    assertEquals(2, groupEnumValues.size)
    assertEquals("group1", groupEnumValues[0].display)
    assertEquals("group2", groupEnumValues[1].display)
  }

}