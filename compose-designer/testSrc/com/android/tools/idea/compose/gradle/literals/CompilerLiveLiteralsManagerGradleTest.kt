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
package com.android.tools.idea.compose.gradle.literals

import com.android.tools.idea.compose.gradle.DEFAULT_KOTLIN_VERSION
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.editors.literals.CompilerLiveLiteralsManager
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.withKotlin
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CompilerLiveLiteralsManagerGradleTest {
  @get:Rule val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)
  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, AGP_CURRENT.withKotlin(DEFAULT_KOTLIN_VERSION))
    projectRule.invokeTasks("compileDebugSources")
  }

  @Test
  fun testLiteralFinding() {
    val activityFile =
      VfsUtil.findRelativeFile(
        SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path,
        ProjectRootManager.getInstance(fixture.project).contentRoots[0]
      )!!
    val psiFile = runReadAction { PsiManager.getInstance(fixture.project).findFile(activityFile)!! }

    runBlocking {
      val literals = CompilerLiveLiteralsManager.getInstance().find(psiFile)
      assertTrue(literals.hasCompilerLiveLiteral(psiFile, 759))
      assertFalse(literals.hasCompilerLiveLiteral(psiFile, 780))
    }
  }
}
