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
package com.android.tools.idea.gradle.project.upgrade.integration

import com.android.testutils.junit4.OldAgpTest
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.annotations.Contract
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunsInEdt
@OldAgpTest
@RunWith(Parameterized::class)
class ProjectStatesValidationTest : ProjectsUpgradeTestBase() {

  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> = allBaseProjectsForCurrentRunner()
      .onEach { println("Test project state validity $it") }
  }

  @JvmField
  @Parameterized.Parameter(0)
  var projectState: AUATestProjectState? = null

  @Test
  fun testProjectSyncs() {
    loadAUATestProject(projectState!!)
  }
}

@RunsInEdt
@OldAgpTest
@RunWith(Parameterized::class)
class ProjectMinimalUpgradeTest : ProjectsUpgradeTestBase() {

  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> = generateAllTestCases()
      .filter { it.from.minimalState && it.to.minimalState }
      .onEach { println("Test min upgrade from ${it.from} to ${it.to}") }
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testCase: UpgradeTestCase? = null

  @Test
  fun testMinimalProjectUpgrade() {
    doTestMinimalUpgrade(testCase!!.from, testCase!!.to)
  }
}

@RunsInEdt
@OldAgpTest
@RunWith(Parameterized::class)
class ProjectFullUpgradeTest : ProjectsUpgradeTestBase() {

  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> = generateAllTestCases()
      .filter { !it.to.minimalState }
      .onEach { println("Test full upgrade from ${it.from} to ${it.to}") }
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testCase: UpgradeTestCase? = null

  @Test
  fun testFullProjectUpgrade() {
    doTestFullUpgrade(testCase!!.from, testCase!!.to)
  }
}

@RunsInEdt
@Ignore
class ManualUtilityTest : ProjectsUpgradeTestBase() {

  @Test
  fun testProjectUpgrade() {
    doTestMinimalUpgrade(AUATestProjectState.ALL_DEPRECATIONS_DEV_MIN, AUATestProjectState.ALL_DEPRECATIONS_DEV_MIN)
  }

  @Test
  fun testProjectSyncs() {
    loadAUATestProject(AUATestProjectState.ALL_DEPRECATIONS_DEV_MIN)
  }
}
