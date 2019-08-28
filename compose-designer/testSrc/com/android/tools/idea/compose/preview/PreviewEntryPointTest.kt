/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.registerExtension
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection

class PreviewEntryPointTest : ComposeLightJavaCodeInsightFixtureTestCase() {
  init {
    // Kotlin UnusedSymbolInspection caches the extensions during the initialization so, unfortunately we have to do this to ensure
    // our entry point detector is registered early enough
    ApplicationManager.getApplication().registerExtension(
                                       ExtensionPointName<PreviewEntryPoint>("com.intellij.deadCode"),
                                       PreviewEntryPoint(),
                                       testRootDisposable)
  }

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UnusedSymbolInspection() as InspectionProfileEntry)
  }

  fun testFindPreviewAnnotations() {
    @Language("kotlin")
    val fileContent = """
      import androidx.ui.tooling.preview.Preview
      import androidx.compose.Composable

      @Composable
      @Preview
      fun Preview1() {
      }

      fun NotUsed() {
      }

      @Composable
      @Preview
      fun Preview2() {
      }

      @Preview
      fun NotAComposable() {
      }
    """.trimIndent()

    myFixture.configureByText("Test.kt", fileContent)
    assertEquals("Function \"NotUsed\" is never used",
                 myFixture.doHighlighting().single { it?.description?.startsWith("Function") ?: false }.description)
  }
}