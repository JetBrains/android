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
package com.android.tools.compose

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.inspections.FunctionNameInspection

/**
 * Test for [ComposeSuppressor].
 */
class ComposeSuppressorTest : JavaCodeInsightFixtureTestCase() {

  fun testFunctionNameWarning(): Unit = myFixture.run {
    enableInspections(FunctionNameInspection::class.java)
    stubComposableAnnotation(ComposeFqNames.root)

    val file = addFileToProject(
      "src/com/example/views.kt",
      """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun MyView() {}

      fun <weak_warning descr="Function name 'NormalFunction' should start with a lowercase letter">NormalFunction</weak_warning>() {}
      """.trimIndent()
    )

    configureFromExistingVirtualFile(file.virtualFile)
    checkHighlighting()
  }
}
