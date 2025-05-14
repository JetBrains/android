/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.runsGradlePhasedSync

import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.requestSyncAndWait
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private val PROPERTIES_WITH_KNOWN_RESYNC_ISSUES = setOf(
  // TODO(b/384022658): Content root related
  // Variant specific paths and info from AndroidProject model is missing.
  "CONENT_ENTRY", // Yes, typo

  // TODO(b/384022658): Related to module sources
  "ORDER_ENTRY (<Module source>)",

  // TODO(b/384022658): External module options related
  "ExternalModuleGroup",
  "ExternalModuleVersion",
  "LinkedProjectId",

  // TODO(b/384022658): JDK related
  "JDK",
  // This should be nested under JDK, but isn't by mistake I think, so need to add it here explicitly
  "*isInherited",

  // TODO(b/384022658)
  "Classes"
)


fun ModuleDumpWithType.filterOutKnownResyncIssues() = copy(
   entries = entries.filter { line ->
      PROPERTIES_WITH_KNOWN_RESYNC_ISSUES.none { line.contains("/$it") }
    }
  )



@RunWith(Parameterized::class)
class PhasedSyncResyncTests(val testProject: TestProject) : PhasedSyncSnapshotTestBase() {
  @get:Rule val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testResync() {
    setupPhasedSyncIntermediateStateCollector(projectRule.testRootDisposable)

    val preparedProject = projectRule.prepareTestProject(testProject)
    preparedProject.open({ it.copy(expectedSyncIssues = testProject.expectedSyncIssues) }) { project: Project ->
      val firstFullSync = project.dumpModules(isAndroidByPath)
      project.requestSyncAndWait(ignoreSyncIssues = testProject.expectedSyncIssues, waitForIndexes = false)
      val secondFullSync = project.dumpModules(isAndroidByPath)
      val secondIntermediateSync = intermediateDump.copy()
      Truth.assertWithMessage("Comparing full sync states")
        .that(secondFullSync.join())
        .isEqualTo(firstFullSync.join())


      Truth.assertWithMessage("Comparing resync intermediate sync state to full state")
        .that(secondIntermediateSync.filterOutKnownResyncIssues().join())
        .isEqualTo(secondFullSync.filterOutKnownResyncIssues().join())
    }
  }


  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testParameters(): Collection<*>  = phasedSyncTestProjects.filterNot {
      setOf(
        // TODO(b/384022658): There is an issue regarding the full sync regarding this project, it seems to create duplicate
        //  library dependencies for some modules. Probably has to do with module libraries.
        TestProject.KOTLIN_MULTIPLATFORM_WITHJS,
        // TODO(b/384022658): Handle spaces in the root project name (settings.gradle) correctly
        TestProject.TWO_JARS,
        TestProject.ANDROID_KOTLIN_MULTIPLATFORM,
      ).contains(it)
    }
  }
}
