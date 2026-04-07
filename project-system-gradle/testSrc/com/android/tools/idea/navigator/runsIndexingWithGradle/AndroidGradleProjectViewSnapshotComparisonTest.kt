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
package com.android.tools.idea.navigator.runsIndexingWithGradle

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectOther
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_MODELS_UPDATED_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.ProjectViewSettings
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.dumpAndroidProjectView
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.IconManager
import com.intellij.ui.icons.CoreIconManager
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class AndroidGradleProjectViewSnapshotComparisonTest : SnapshotComparisonTest {
  @get:Rule val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule var testName = TestName()

  @Test
  fun testKotlinKapt() {
    val preparedProject = projectRule.prepareTestProject(TestProject.KOTLIN_KAPT)
    preparedProject.open { project ->
      project.buildAndWait { invoker -> invoker.assemble() }
      val text = invokeAndWaitIfNeeded {
        project.dumpAndroidProjectView(ProjectViewSettings(flattenPackages = true), Unit, { _, _ -> Unit })
      }
      assertIsEqualToSnapshot(text)
    }
  }

  @Test
  @RunsInEdt
  fun testJpsWithQualifiedNames() {
    val preparedProject = projectRule.prepareTestProject(TestProjectOther.JPS_WITH_QUALIFIED_NAMES)
    preparedProject.open { project ->
      val text = project.dumpAndroidProjectView()
      assertIsEqualToSnapshot(text)
    }
  }

  @Test
  @RunsInEdt
  fun testMissingImlIsIgnored() {
    AndroidGradleTests.addJdk8ToTableButUseCurrent()
    try {
      val preparedProject = projectRule.prepareTestProject(TestProjectOther.SIMPLE_APPLICATION_CORRUPTED_MISSING_IML_40)
      val text = preparedProject.open { project: Project -> project.dumpAndroidProjectView() }

      assertIsEqualToSnapshot(text)
    } finally {
      AndroidGradleTests.restoreJdk()
    }
  }

  @Test
  @RunsInEdt
  fun testTestSuites() {
    try {
      val preparedProject = projectRule.prepareTestProject(TestProject.TEST_SUITES)
      val text = preparedProject.open { project: Project -> project.dumpAndroidProjectView() }

      assertIsEqualToSnapshot(text)
    } finally {
      AndroidGradleTests.restoreJdk()
    }
  }

  @Test
  @RunsInEdt
  fun testFirstAndroidModelsUpdate_PhasedSync() {
    enablePhasedSync()
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val (earlyViewDump, _) = preparedProject.dumpAndroidViewBeforeAndAfterSyncFinishes()
    assertIsEqualToSnapshot(earlyViewDump)
    clearPhasedSync()
  }

  private fun PreparedTestProject.dumpAndroidViewBeforeAndAfterSyncFinishes(): Pair<String, String> {
    var earlyViewDump: String? = null
    return open(
      updateOptions = { options ->
        options.copy(
          onProjectCreated = {
            messageBus
              .connect()
              .subscribe(
                PROJECT_SYSTEM_MODELS_UPDATED_TOPIC,
                ProjectSystemSyncManager.AndroidModelsUpdatedListener {
                  invokeAndWaitIfNeeded {
                    if (earlyViewDump == null) {
                      earlyViewDump = this.dumpAndroidProjectView().removeFileIconAddress()
                    }
                  }
                },
              )
          }
        )
      }
    ) { project: Project ->
      earlyViewDump.orEmpty() to project.dumpAndroidProjectView().removeFileIconAddress()
    }
  }

  private fun enablePhasedSync() {
    StudioFlags.PHASED_SYNC_ENABLED.override(true)
    Registry.get("gradle.phased.sync.enabled").setValue(true)
  }

  private fun clearPhasedSync() {
    StudioFlags.PHASED_SYNC_ENABLED.clearOverride()
    Registry.get("gradle.phased.sync.enabled").setValue(StudioFlags.PHASED_SYNC_ENABLED.get())
  }

  internal fun String.removeFileIconAddress(): String = replace(Regex("FileIcon@[a-z0-9]*"), "FileIcon@0")

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/projectViews"

  override fun getName(): String = testName.methodName

  init {
    // Avoid depending on the execution order and initializing icons with dummies.
    try {
      IconManager.Companion.activate(CoreIconManager())
      IconLoader.activate()
    } catch (e: Throwable) {
      e.printStackTrace()
    }
  }
}
