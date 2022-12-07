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
package com.android.tools.idea.nav.safeargs.gradle

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.nav.safeargs.safeArgsModeTracker
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Verify that we can sync a Gradle project that applies the safe args plugin.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class SafeArgsModeSyncGradlePluginTest(val params: TestParams) {
  data class TestParams(val project: String, val flagEnabled: Boolean, val mode: SafeArgsMode)

  companion object {
    @Suppress("unused") // Accessed via reflection by JUnit
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val parameters = listOf(
      TestParams(TestDataPaths.PROJECT_USING_JAVA_PLUGIN, true, SafeArgsMode.JAVA),
      TestParams(TestDataPaths.PROJECT_USING_KOTLIN_PLUGIN, true, SafeArgsMode.KOTLIN),
      TestParams(TestDataPaths.PROJECT_USING_JAVA_PLUGIN, false, SafeArgsMode.NONE),
      TestParams(TestDataPaths.PROJECT_USING_KOTLIN_PLUGIN, false, SafeArgsMode.NONE))
  }

  private val projectRule = AndroidGradleProjectRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @get:Rule
  val restoreSafeArgsFlagRule = FlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)


  private val fixture get() = projectRule.fixture as JavaCodeInsightTestFixture
  private var modificationCountBaseline = Long.MIN_VALUE

  @Before
  fun setUp() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(params.flagEnabled)

    modificationCountBaseline = projectRule.project.safeArgsModeTracker.modificationCount

    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(params.project)
  }

  @Test
  fun verifyExpectedSafeMode() {
    projectRule.requestSyncAndWait()

    val facet = projectRule.androidFacet(":app")
    assertThat(facet.safeArgsMode).isEqualTo(params.mode)

    if (params.flagEnabled) {
      assertThat(projectRule.project.safeArgsModeTracker.modificationCount).isGreaterThan(modificationCountBaseline)
    }
    else {
      assertThat(projectRule.project.safeArgsModeTracker.modificationCount).isEqualTo(0)
    }
  }
}