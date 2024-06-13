/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.SourceFolder
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.TestFunctionNameInspection
import org.jetbrains.kotlin.idea.k1.codeinsight.inspections.FunctionNameInspection as FunctionNameInspectionForK1
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.FunctionNameInspection
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposeSuppressorTest {

  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    fixture.enableInspections(
      if (KotlinPluginModeProvider.isK2Mode()) {
        FunctionNameInspection::class.java
      } else {
        FunctionNameInspectionForK1::class.java
      }
    )
    fixture.enableInspections(TestFunctionNameInspection::class.java)
    fixture.stubComposableAnnotation()

    val module = projectRule.project.modules.single()
    val androidTestSourceRoot = fixture.tempDirFixture.findOrCreateDir("src/androidTest")
    runInEdt {
      ApplicationManager.getApplication().runWriteAction<SourceFolder> {
        PsiTestUtil.addSourceRoot(module, androidTestSourceRoot, true)
      }
    }
  }

  @Test
  fun testFunctionNameWarning() {
    val file =
      fixture.addFileToProject(
        "src/main/com/example/views.kt",
        """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun MyView() {}

      fun <weak_warning descr="Function name 'NormalFunction' should start with a lowercase letter">NormalFunction</weak_warning>() {}
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testFunctionNameWarningInAndroidTestFile() {
    val file =
      fixture.addFileToProject(
        "src/androidTest/com/example/views.kt",
        """
      package com.example

      import androidx.compose.runtime.Composable

      @Composable
      fun MyView() {}

      fun <weak_warning descr="Test function name 'NormalFunction' should start with a lowercase letter">NormalFunction</weak_warning>() {}
      """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }
}
