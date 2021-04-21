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

import com.android.tools.compose.ComposeLibraryNamespace
import com.android.tools.idea.compose.preview.addFileToProjectAndInvalidate
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubConfigurationAsLibrary
import org.jetbrains.android.compose.stubDevicesAsLibrary
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

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
  private lateinit var project: Project
  private lateinit var module: Module

  @Before
  fun setup() {
    project = rule.project
    module = rule.fixture.module
    ConfigurationManager.getOrCreateInstance(module)
    rule.fixture.stubConfigurationAsLibrary()
    rule.fixture.stubDevicesAsLibrary(composeLibraryNamespace.previewPackage)
  }

  @RunsInEdt
  @Test
  fun testValuesProvider() {
    val valuesProvider = PsiCallEnumSupportValuesProvider.createPreviewValuesProvider(module, composeLibraryNamespace, null)
    val uiModeValues = valuesProvider.getValuesProvider("uiMode")!!.invoke()
    assertEquals(4, uiModeValues.size)
    assertEquals("Normal", uiModeValues[0].display)
    assertEquals("Undefined", uiModeValues[1].display)
    assertEquals("Desk", uiModeValues[2].display)
    assertEquals("Car", uiModeValues[3].display)

    val deviceValues = valuesProvider.getValuesProvider("device")!!.invoke()
    assertEquals(6, deviceValues.size)
    assertEquals("Default", deviceValues[0].display)
    assertEquals("Pixel ", deviceValues[1].display)
    assertEquals("Pixel 4", deviceValues[2].display)
    assertEquals("Nexus 10", deviceValues[3].display)
    assertEquals("Nexus 7", deviceValues[4].display)
    assertEquals("Automotive 1024p", deviceValues[5].display)
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