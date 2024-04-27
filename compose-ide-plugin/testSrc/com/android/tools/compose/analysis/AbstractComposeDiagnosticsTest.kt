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
package com.android.tools.compose.analysis

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib
import org.junit.Rule

abstract class AbstractComposeDiagnosticsTest {
  @get:Rule val androidProject = AndroidProjectRule.inMemory()

  protected fun doTest(
    expectedText: String,
    verifyHighlights: ((List<Pair<HighlightInfo, Int>>) -> Unit)? = null
  ): Unit =
    androidProject.fixture.run {
      setUpCompilerArgumentsForComposeCompilerPlugin(project)

      stubComposeRuntime()
      stubKotlinStdlib()

      val file =
        addFileToProject(
          "src/com/example/test.kt",
          """
      $suppressAnnotation
      package com.example
      $expectedText
      """
            .trimIndent()
        )

      configureFromExistingVirtualFile(file.virtualFile)
      checkHighlighting()
      verifyHighlights?.let {
        it(
          doHighlighting().map {
            it to StringUtil.offsetToLineNumber(file.text, it.actualStartOffset)
          }
        )
      }
    }
}
