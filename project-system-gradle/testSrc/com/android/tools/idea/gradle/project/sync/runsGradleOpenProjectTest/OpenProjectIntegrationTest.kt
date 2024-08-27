/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.runsGradleOpenProjectTest

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.android.tools.idea.testing.verifySyncSkipped
import com.android.tools.idea.testing.verifySyncSuccessful
import com.android.tools.idea.testing.withoutKtsRelatedIndexing
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.roots.ModuleRootManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import org.gradle.util.GradleVersion
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path

@RunWith(JUnit4::class)
@RunsInEdt
class OpenProjectIntegrationTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()!!

  @After
  fun tearDown() {
    AndroidGradleTests.restoreJdk()
  }

  @Test
  fun testReopenProject() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val before = preparedProject.open { project -> project.saveAndDump() }
    val after = preparedProject.open { project ->
      verifySyncSkipped(project, projectRule.testRootDisposable)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenProject_kmpWithJs() {
    val preparedProject = projectRule.prepareTestProject(TestProject.KOTLIN_MULTIPLATFORM_WITHJS)
    val before = preparedProject.open { project -> project.saveAndDump() }
    val after = preparedProject.open { project ->
      verifySyncSkipped(project, projectRule.testRootDisposable)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenProject_kmpWithAndroid() {
    val preparedProject = projectRule.prepareTestProject(TestProject.ANDROID_KOTLIN_MULTIPLATFORM)
    val before = preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { project ->
      project.saveAndDump()
    }
    val after = preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { project ->
      verifySyncSkipped(project, projectRule.testRootDisposable)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenProject_withCustomEntry() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val before = preparedProject.open { project ->
      runWriteAction {
        val appMainModule = project.gradleModule(":app")!!.getMainModule()
        val modifieableModule = ModuleRootManagerEx.getInstanceEx(appMainModule).modifiableModel
        val abc = appMainModule.fileUnderGradleRoot("src")!!.createChildDirectory("test", "abc")
        modifieableModule.addContentEntry(abc)
        modifieableModule.commit()
      }
      project.saveAndDump()
    }
    val after = preparedProject.open { project ->
      verifySyncSkipped(project, projectRule.testRootDisposable)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReimportProject() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val before = preparedProject.open { project -> project.saveAndDump() }
    FileUtil.delete(File(preparedProject.root, ".idea"))
    val after = preparedProject.open { project ->
      // Synced again.
      Truth.assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult())
        .isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenKaptProject() {
    val preparedProject = projectRule.prepareTestProject(TestProject.KOTLIN_KAPT)
    val before = preparedProject.open { project -> project.saveAndDump() }
    val after = preparedProject.open { project ->
      verifySyncSkipped(project, projectRule.testRootDisposable)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenProjectAfterFailedSync() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val buildFile = VfsUtil.findFileByIoFile(preparedProject.root.resolve("app/build.gradle"), true)!!

    val (snapshots, lastSyncFinishedTimestamp) = preparedProject.open { project ->
      val initial = project.saveAndDump()
      runWriteAction {
        buildFile.setBinaryContent("*bad*".toByteArray())
      }
      AndroidGradleTests.syncProject(project, GradleSyncInvoker.Request.testRequest()) {
        // Do not check status.
      }
      Truth.assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult())
        .isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
      (initial to project.saveAndDump()) to GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp
    }
    val (initial, before) = snapshots
    val after = preparedProject.open(
      updateOptions = {
        it.copy(
          verifyOpened = { project ->
            Truth.assertThat(
              project.getProjectSystem()
                .getSyncManager()
                .getLastSyncResult()
            ).isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
          }
        )
      }
    ) { project ->
      // Make sure we tried to sync.
      Truth.assertThat(GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp).isNotEqualTo(lastSyncFinishedTimestamp)
      project.saveAndDump()
    }
    Truth.assertThat(before).isEqualTo(initial)
    // TODO(b/211782178): assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenCompositeBuildProject() {
    val preparedProject = projectRule.prepareTestProject(TestProject.COMPOSITE_BUILD)
    val before = preparedProject.open { project -> project.saveAndDump() }
    val after = preparedProject.open { project ->
      verifySyncSkipped(project, projectRule.testRootDisposable)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }

  @Test
  fun testReopenPsdSampleGroovy() {
    val preparedProject = projectRule.prepareTestProject(TestProject.PSD_SAMPLE_GROOVY)
    val before = preparedProject.open { project -> project.saveAndDump() }
    val after = preparedProject.open { project ->
      verifySyncSkipped(project, projectRule.testRootDisposable)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }

  @Test
  fun testOpen36Project() {
    AndroidGradleTests.addJdk8ToTableButUseCurrent()
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.RUN_APP_36)
    preparedProject.open { project ->
      val androidTestRunConfiguration =
        RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<AndroidTestRunConfiguration>().singleOrNull()
      Truth.assertThat(androidTestRunConfiguration?.name).isEqualTo("All Tests Sub 36")

      val runConfigurations = RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<ModuleBasedConfiguration<*, *>>()
      // Existing run configuration will not be able to find the modules since we enabled qualified module names and module per source set
      // As such these existing configuration will be mapped to null and a new configuration for the app module created.
      // We don't remove this configuration to avoid losing importing config the user has set up.
      Truth.assertThat(runConfigurations.associate { it.name to it.configurationModule?.module?.name }).isEqualTo(mapOf(
        "app" to "My36.app.main",
        "app.sub36" to "My36.app.sub36.main",
        "sub36" to null,
        "All Tests Sub 36" to null
      ))
    }
  }

  @Test
  fun testOpen36ProjectWithoutModules() {
    AndroidGradleTests.addJdk8ToTableButUseCurrent()
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.RUN_APP_36)
    runWriteAction {
      val projectRootVirtualFile = VfsUtil.findFileByIoFile(preparedProject.root, false)!!
      projectRootVirtualFile.findFileByRelativePath(".idea/modules.xml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("app/app.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("app/sub36/sub36.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("My36.iml")!!.delete("test")
    }

    preparedProject.open { project ->
      val runConfigurations = RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<ModuleBasedConfiguration<*, *>>()
      // Existing run configuration will not be able to find the modules since we enabled qualified module names and module per source set
      // As such these existing configuration will be mapped to null and a new configuration for the app module created.
      // We don't remove this configuration to avoid losing importing config the user has set up.
      Truth.assertThat(runConfigurations.associate { it.name to it.configurationModule?.module?.name }).isEqualTo(mapOf(
        "app" to "My36.app.main",
        "app.sub36" to "My36.app.sub36.main",
        "sub36" to null,
        "All Tests Sub 36" to null
      ))
    }
  }

  @Test
  fun testReopenAndResync() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    val debugBefore = preparedProject.open { project: Project ->
      runWriteAction {
        // Modify the project build file to ensure the project is synced when opened.
        project.gradleModule(":")!!.fileUnderGradleRoot("build.gradle")!!.also { file ->
          file.setBinaryContent((String(file.contentsToByteArray()) + " // ").toByteArray())
        }
      }
      project.saveAndDump()
    }
    val reopenedDebug = preparedProject.open { project ->
      // TODO(b/146535390): Uncomment when sync required status survives restarts.
      //  assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
      project.saveAndDump()
    }
    Truth.assertThat(reopenedDebug).isEqualTo(debugBefore)
  }

  @Test
  fun testResyncPsdDependency() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_DEPENDENCY)
    preparedProject.open { project: Project ->
      val firstSync = project.saveAndDump()
      project.requestSyncAndWait()
      val secondSync = project.saveAndDump()
      Truth.assertThat(firstSync).isEqualTo(secondSync)
    }
  }

  @Test
  fun testGradleVersionAfterClose() {
    val preparedProjectA = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION, name = "A")
    val preparedProjectB = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION_PLUGINS_DSL, name = "B")
    val syncResult = preparedProjectA.open { A ->
      preparedProjectB.open { B ->
        runInEdtAndWait {
          ProjectManager.getInstance().closeAndDispose(A)
        }
        B.requestSyncAndWait()
        val wrapper = GradleWrapper.find(B)!!
        wrapper.updateDistributionUrl(GradleVersion.version("7.999"))

        AndroidGradleTests.syncProject(B, GradleSyncInvoker.Request.testRequest()) {
          // Do not check status.
        }
        B.getProjectSystem().getSyncManager().getLastSyncResult()
      }
    }
    Truth.assertThat(syncResult).isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
  }

  /** Regression tests for b/300512355. */
  @Test
  fun testReOpenWithCachesButNoModules() {
    // Caching is disabled by default in unit tests, enable it for this test case
    WorkspaceModelCacheImpl.forceEnableCaching(projectRule.testRootDisposable)

    val preparedProject = projectRule.prepareTestProject(TestProject.PSD_SAMPLE_GROOVY)
    val (before: String, externalConfigurationDir: Path) = preparedProject.open { project ->
      Pair(project.saveAndDump(), project.getExternalConfigurationDir())
    }

    // Simulate corrupt external configuration caches
    externalConfigurationDir.deleteRecursively()

    val after = preparedProject.open { project ->
      verifySyncSuccessful(project, projectRule.testRootDisposable)
      project.saveAndDump()
    }
    Truth.assertThat(after).isEqualTo(before)
  }
}