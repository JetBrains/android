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
package com.android.tools.idea.rendering

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.withKotlin
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.android.uipreview.StudioModuleClassLoaderManager
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule


open class ComposeRenderTestBase {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  open fun setUp() {
    RenderTestUtil.beforeRenderTestCase()
    StudioRenderService.setForTesting(projectRule.project, createNoSecurityRenderService())
    val baseTestPath = TestUtils.resolveWorkspacePath("tools/adt/idea/designer-perf-tests/testData").toString()
    projectRule.fixture.testDataPath = baseTestPath
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, AGP_CURRENT.withKotlin("1.7.20"))

    projectRule.invokeTasks("compileDebugSources").apply {
      buildError?.printStackTrace()
      Assert.assertTrue("The project must compile correctly for the test to pass", isBuildSuccessful)
    }

    StudioModuleClassLoaderManager.get().setCaptureClassLoadingDiagnostics(true)
  }

  @After
  open fun tearDown() {
    StudioModuleClassLoaderManager.get().setCaptureClassLoadingDiagnostics(false)
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
    StudioRenderService.setForTesting(projectRule.project, null)
  }
}

fun AndroidGradleProjectRule.buildAndAssertSuccess(task: String = "assemble") {
  val buildResult = invokeTasks(task)
  Assert.assertTrue(
    """
        Build failed with:

        ${buildResult.buildError}
      """.trimIndent(),
    buildResult.isBuildSuccessful
  )
}
