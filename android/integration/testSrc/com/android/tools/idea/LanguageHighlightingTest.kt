/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AnalysisResult
import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.MavenRepo
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LanguageHighlightingTest {
  @get:Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @Test
  fun kotlinHighlighting() {
    val project = AndroidProject("tools/adt/idea/android/integration/testData/languagehighlighting")

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo("tools/adt/idea/android/integration/languagehighlighting_deps.manifest"))

    system.runStudio(project).use { studio ->
      studio.waitForSync()
      studio.waitForIndex()

      val analysisResults = studio.analyzeFile("src/main/java/com/example/languagehighlighting/MainActivity.kt")

      // Validate Kotlin highlighting by spot-checking some of the expected tokens that should be highlighted.

      // 1. `override` keyword
      assertThat(analysisResults).contains(AnalysisResult(
        HighlightInfoType.SYMBOL_TYPE_SEVERITY,
        "override",
        /* description = */ null,
        /* toolId = */ null,
        /* lineNumber = */ 8))

      // 2. Warning from Kotlin plugin.
      assertThat(analysisResults).contains(AnalysisResult(
        HighlightSeverity.WEAK_WARNING,
        "setText",
        /* description = */ "Use of setter method instead of property access syntax",
        /* toolId = */ "UsePropertyAccessSyntax",
        /* lineNumber = */ 11))

      // 3. Warning from Android plugin.
      assertThat(analysisResults).contains(AnalysisResult(
        HighlightSeverity.WARNING,
        "Hello Minimal World!",
        /* description = */ "String literal in `setText` can not be translated. Use Android resources instead.",
        /* toolId = */ null,
        /* lineNumber = */ 11))

      // 4. Error from Kotlin plugin.
      assertThat(analysisResults).contains(AnalysisResult(
        HighlightSeverity.ERROR,
        "someMethodThatDoesNotExist",
        /* description = */ "Unresolved reference: someMethodThatDoesNotExist",
        /* toolId = */ null,
        /* lineNumber = */ 15))
    }
  }
}