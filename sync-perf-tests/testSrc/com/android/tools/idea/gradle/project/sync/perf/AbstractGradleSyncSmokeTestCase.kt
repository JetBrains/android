/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.perf

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTests.overrideJdkTo8
import com.android.tools.idea.testing.AndroidGradleTests.restoreJdk
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
abstract class AbstractGradleSyncSmokeTestCase {
  abstract val relativePath: String
  protected open val buildTask: String? = "assembleDebug"
  protected open val buildTaskTimeout: Long? = null

  protected val projectRule = AndroidGradleProjectRule()
  @get:Rule
  val ruleChain = org.junit.rules.RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  @Throws(Exception::class)
  open fun setUp() {
    val projectSettings = GradleProjectSettings()
    projectSettings.distributionType = DistributionType.DEFAULT_WRAPPED
    GradleSettings.getInstance(projectRule.project).linkedProjectsSettings = listOf(projectSettings)

    projectRule.fixture.testDataPath = AndroidTestBase.getModulePath("sync-perf-tests") + File.separator + "testData"
    disableExpensivePlatformAssertions(projectRule.fixture)
  }

  /**
   * Verify that the test project is able to open and sync without errors.
   * @throws Exception
   */
  @Throws(java.lang.Exception::class)
  @Test
  open fun testSyncsAndBuilds() {
    verifySync()
    verifyBuild()
  }

  private fun verifySync() {
    // Load also syncs, this should be enough to confirm sync is successful
    projectRule.loadProject(relativePath)
  }

  private fun verifyBuild() {
    if (buildTask == null) return
    ApplicationManager.getApplication().invokeAndWait {
      projectRule.invokeTasks(buildTaskTimeout, buildTask!!).apply {
        buildError?.printStackTrace()
        assertTrue("The project must compile correctly for the test to pass", isBuildSuccessful)
      }
    }
  }
}

fun disableExpensivePlatformAssertions(fixture: CodeInsightTestFixture) {
  ApplicationManagerEx.setInStressTest(true)
  Disposer.register(fixture.testRootDisposable, {
    ApplicationManagerEx.setInStressTest(false)
  })
}
