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
package com.android.tools.idea.nav.safeargs.cache.gradle

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.nav.safeargs.extensions.getContents
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ShortNamesCacheTestMultiJavaModules {
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val restoreSafeArgsFlagRule = FlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Before
  fun setUp() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(TestDataPaths.SIMPLE_JAVA_PROJECT)
    NavigationResourcesModificationListener.ensureSubscribed(fixture.project)
  }

  /**
   *  Project structure:
   *  base app module --> lib1 dep module(safe arg mode is off) --> lib2 dep module(safe arg mode is on)
   *
   *  So light classes from lib2 module should be exposed, but light classes from lib1 should not be exposed.
   */
  @Test
  fun multiModuleTest() {
    projectRule.requestSyncAndWait()
    val cache = PsiShortNamesCache.getInstance(fixture.project)

    // Check light arg classes
    assertThat(cache.getContents("FirstFragmentArgs", fixture.project)).containsExactly(
      "com.example.myapplication.FirstFragmentArgs",
      "com.example.mylibrary2.FirstFragmentArgs"
    )

    assertThat(cache.getContents("SecondFragmentArgs", fixture.project)).containsExactly(
      "com.example.myapplication.SecondFragmentArgs"
    )


    // Check light direction classes
    assertThat(cache.getContents("FirstFragmentDirections", fixture.project)).containsExactly(
      "com.example.myapplication.FirstFragmentDirections",
      "com.example.mylibrary2.FirstFragmentDirections"
    )

    assertThat(cache.getContents("SecondFragmentDirections", fixture.project)).containsExactly(
      "com.example.myapplication.SecondFragmentDirections"
    )

    // Check light builder classes
    assertThat(cache.getContents("Builder", fixture.project)).containsAllOf(
      "com.example.myapplication.FirstFragmentArgs.Builder",
      "com.example.mylibrary2.FirstFragmentArgs.Builder"
    )

    assertThat(cache.getContents("Builder", fixture.project)).contains(
      "com.example.myapplication.SecondFragmentArgs.Builder"
    )
  }
}