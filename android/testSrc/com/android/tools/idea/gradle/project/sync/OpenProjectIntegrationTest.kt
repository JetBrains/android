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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testing.AndroidGradleTests.addJdk8ToTableButUseCurrent
import com.android.tools.idea.testing.AndroidGradleTests.restoreJdk
import com.android.tools.idea.testing.AndroidGradleTests.syncProject
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.verifySyncSkipped
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil

class OpenProjectIntegrationTest : GradleSyncIntegrationTestCase(), GradleIntegrationTest {
  override fun tearDown() {
    restoreJdk()
    super.tearDown()
  }

  fun testReopenProject() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { }
    openPreparedProject("project") { project ->
      verifySyncSkipped(project, testRootDisposable)
    }
  }

  fun testReopenKaptProject() {
    prepareGradleProject(TestProjectPaths.KOTLIN_KAPT, "project")
    openPreparedProject("project") { }
    openPreparedProject("project") { project ->
      verifySyncSkipped(project, testRootDisposable)
    }
  }

  fun testReopenProjectAfterFailedSync() {
    val root = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    val buildFile = VfsUtil.findFileByIoFile(root.resolve("app/build.gradle"), true)!!

    val lastSyncFinishedTimestamp = openPreparedProject("project") { project ->
      runWriteAction {
        buildFile.setBinaryContent("*bad*".toByteArray())
      }
      syncProject(project, GradleSyncInvoker.Request.testRequest())
      assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
      GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp
    }

    openPreparedProject(
      "project",
      verifyOpened = { project ->
        assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
      }
    ) {
      // Make sure we tried to sync.
      assertThat(GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp).isNotEqualTo(lastSyncFinishedTimestamp)
    }
  }

  fun testReopenCompositeBuildProject() {
    prepareGradleProject(TestProjectPaths.COMPOSITE_BUILD, "project")
    openPreparedProject("project") { }
    openPreparedProject("project") { project ->
      verifySyncSkipped(project, testRootDisposable)
    }
  }

  fun testOpen36Project() {
    addJdk8ToTableButUseCurrent()
    prepareGradleProject(TestProjectPaths.RUN_APP_36, "project")
    openPreparedProject("project") { project ->
      val androidTestRunConfiguration =
        RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<AndroidTestRunConfiguration>().singleOrNull()
      assertThat(androidTestRunConfiguration?.name).isEqualTo("All Tests Sub 36")

      val runConfigurations = RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<ModuleBasedConfiguration<*, *>>()
      assertThat(runConfigurations.associate { it.name to it.configurationModule?.module?.name }).isEqualTo(mapOf(
        "app" to "My36.app",
        "sub36" to "My36.app.sub36",
        "All Tests Sub 36" to "My36.app.sub36"
      ))
    }
  }

  fun testOpen36ProjectWithoutModules() {
    addJdk8ToTableButUseCurrent()
    val projectRoot = prepareGradleProject(TestProjectPaths.RUN_APP_36, "project")
    runWriteAction {
      val projectRootVirtualFile = VfsUtil.findFileByIoFile(projectRoot, false)!!
      projectRootVirtualFile.findFileByRelativePath(".idea/modules.xml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("app/app.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("app/sub36/sub36.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("My36.iml")!!.delete("test")
    }

    openPreparedProject("project") { project ->
      val runConfigurations = RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<ModuleBasedConfiguration<*, *>>()
      assertThat(runConfigurations.associate { it.name to it.configurationModule?.module?.name }).isEqualTo(mapOf(
        "app" to "My36.app",
        "sub36" to "My36.app.sub36",
        "All Tests Sub 36" to "My36.app.sub36"
      ))
    }
  }
}
