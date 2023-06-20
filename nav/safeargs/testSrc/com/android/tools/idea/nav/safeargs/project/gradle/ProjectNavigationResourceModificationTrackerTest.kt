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
package com.android.tools.idea.nav.safeargs.project.gradle

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.nav.safeargs.extensions.replaceWithoutSaving
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.nav.safeargs.project.ProjectNavigationResourceModificationTracker
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Test that our project-wide modification tracker works across multiple modules.
 *
 * This needs to be a gradle test because that's the only way right now we can support multi-module configurations
 */
@RunsInEdt
class ProjectNavigationResourceModificationTrackerTest {
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
 */
  @Test
  fun multiModuleModificationTrackerTest() {
    projectRule.requestSyncAndWait()
    val baseLineNumber = ProjectNavigationResourceModificationTracker.getInstance(fixture.project).modificationCount

    val navFileInBaseAppModule = projectRule.project.baseDir.findFileByRelativePath(
      "app/src/main/res/navigation/nav_graph.xml")!!

    val navFileInDepModule = projectRule.project.baseDir.findFileByRelativePath(
      "mylibrary2/src/main/res/navigation/libnav_graph.xml")!!

    // modify a nav file in base-app module without saving
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      navFileInBaseAppModule.replaceWithoutSaving("FirstFragment", "FirstFragmentChanged", fixture.project)
    }
    // picked up 1 document change
    assertThat(ProjectNavigationResourceModificationTracker.getInstance(fixture.project).modificationCount).isEqualTo(baseLineNumber + 1)

    // modify a nav file in dep module without saving
    WriteCommandAction.runWriteCommandAction(fixture.project) {
      navFileInDepModule.replaceWithoutSaving("FirstFragment", "FirstFragmentChanged", fixture.project)
    }
    // picked up 1 document change
    assertThat(ProjectNavigationResourceModificationTracker.getInstance(fixture.project).modificationCount).isEqualTo(baseLineNumber + 2)
  }
}