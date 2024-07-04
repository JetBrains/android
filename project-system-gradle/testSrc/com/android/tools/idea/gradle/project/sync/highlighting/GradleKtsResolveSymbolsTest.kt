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
package com.android.tools.idea.gradle.project.sync.highlighting

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class GradleKtsResolveSymbolsTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setup() {
    // skipping compiler highlighter
    AndroidTestBase.unmaskKotlinHighlightVisitor(projectRule.fixture)
  }

  @Test
  fun testSingleModuleApplication() = test(TestProjectPaths.BASIC_KOTLIN_GRADLE_DSL)

  @Test
  fun testSimpleApplication() = test(TestProjectPaths.KOTLIN_GRADLE_DSL)

  private fun test(projectPath: String) {
    projectRule.load(projectPath)

    var ktsFiles: Collection<VirtualFile>? = null
    runReadAction {
      ktsFiles = FilenameIndex.getAllFilesByExt(projectRule.project, GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)
    }
    assertNotEmpty(ktsFiles)

    val ktsHighlightErrors = mutableListOf<HighlightInfo>()
    runInEdtAndWait {
      ktsFiles?.forEach { ktsFile ->
        projectRule.fixture.openFileInEditor(ktsFile)
        projectRule.fixture.doHighlighting(HighlightSeverity.ERROR).also {
          ktsHighlightErrors.addAll(it)
        }
      }
    }
    assertEmpty(ktsHighlightErrors)
  }
}