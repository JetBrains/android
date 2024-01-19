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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.nav.safeargs.module.SafeArgsModeModuleService
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
import org.mockito.Mockito.verify

/** Verify that we can sync a Gradle project that applies the safe args plugin. */
@RunsInEdt
@RunWith(Parameterized::class)
class SafeArgsModeSyncGradlePluginTest(val params: TestParams) {
  data class TestParams(val project: String, val mode: SafeArgsMode)

  companion object {
    @Suppress("unused") // Accessed via reflection by JUnit
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val parameters =
      listOf(
        TestParams(TestDataPaths.PROJECT_USING_JAVA_PLUGIN, SafeArgsMode.JAVA),
        TestParams(TestDataPaths.PROJECT_USING_KOTLIN_PLUGIN, SafeArgsMode.KOTLIN),
      )
  }

  private val projectRule = AndroidGradleProjectRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private var modificationCountBaseline = Long.MIN_VALUE

  @Before
  fun setUp() {
    modificationCountBaseline = projectRule.project.safeArgsModeTracker.modificationCount

    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
  }

  @Test
  fun verifyExpectedSafeMode() {
    val listener = mock<SafeArgsModeModuleService.SafeArgsModeChangedListener>()
    projectRule.project.messageBus
      .connect(fixture.projectDisposable)
      .subscribe(
        SafeArgsModeModuleService.MODE_CHANGED,
        SafeArgsModeModuleService.SafeArgsModeChangedListener { module, mode ->
          listener.onSafeArgsModeChanged(module, mode)
        },
      )

    projectRule.load(params.project)
    projectRule.requestSyncAndWait()

    val facet = projectRule.androidFacet(":app")
    assertThat(facet.safeArgsMode).isEqualTo(params.mode)
    assertThat(projectRule.project.safeArgsModeTracker.modificationCount)
      .isGreaterThan(modificationCountBaseline)
    verify(listener).onSafeArgsModeChanged(facet.module, params.mode)
  }
}
