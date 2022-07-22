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

import com.android.tools.idea.gradle.project.sync.GradleSyncState.Companion.GRADLE_SYNC_TOPIC
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants.BUILTIN_MODULE_DATA_SERVICE_ORDER
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunsInEdt
class PlatformIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testModelBuildServiceInCompositeBuilds() {
    val compositeBuildRoot = prepareGradleProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD, "project")
    CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(projectRule.fixture.testRootDisposable)
    openPreparedProject("project") { project ->
      for (module in ModuleManager.getInstance(project).modules) {
        if (ExternalSystemApiUtil.getExternalModuleType(module) == "sourceSet") continue

        val gradleTestModel: TestGradleModel? = CapturePlatformModelsProjectResolverExtension.getTestGradleModel(module)
        expect.that(gradleTestModel).named("TestGradleModel($module)").isNotNull()

        val gradleParameterizedTestModel: TestParameterizedGradleModel? =
          CapturePlatformModelsProjectResolverExtension.getTestParameterizedGradleModel(module)
        // TODO(b/202448739): Remove `if` when support for parameterized models in included builds is fixed in the IntelliJ platform.
        if (module.getGradleProjectPath()?.buildRoot == toSystemIndependentName(compositeBuildRoot.absolutePath)) {
          expect.that(gradleParameterizedTestModel).named("TestParameterizedGradleModel($module)").isNotNull()
          if (gradleParameterizedTestModel != null) {
            expect.that(gradleParameterizedTestModel.message).named("TestParameterizedGradleModel($module).message")
              .isEqualTo("Parameter: EHLO BuildDir: ${ExternalSystemApiUtil.getExternalProjectPath(module)}/build")
          }
        }
      }
    }
  }

  @Test
  fun testCorrectSyncEventsPublished_successfulSync() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    val log = openProjectWithEventLogging("project")

    expect.that(log).isEqualTo("""
      |started
      |succeeded
      """.trimMargin())
  }

  @Test
  fun testCorrectSyncEventsPublished_reopen() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    openPreparedProject("project") {}
    val log = openProjectWithEventLogging("project")

    expect.that(log).isEqualTo(
      """
      |skipped
      """.trimMargin()
    )
  }

  @Test
  fun testCorrectSyncEventsPublished_badConfig() {
    val path = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    path.resolve("settings.gradle").writeText("***BAD FILE***")

    val log = openProjectWithEventLogging("project")

    expect.that(log).startsWith(
      """
      |started
      |failed:
      """.trimMargin()
    )
    expect.that(log).contains("***BAD FILE***")
  }

  class FailingService: AbstractModuleDataService<ModuleData>() {
    override fun getTargetDataKey(): Key<ModuleData> = ProjectKeys.MODULE
    override fun importData(
      toImport: MutableCollection<out DataNode<ModuleData>>,
      projectData: ProjectData?,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider
    ): Nothing {
      error("Failed!")
    }
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_dataImporterCrashes() {
    (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint(ProjectDataService.EP_NAME)
      .registerExtension(FailingService(), projectRule.testRootDisposable)
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    val log = openProjectWithEventLogging("project")

    assertThat(log).isEqualTo(
      """
      |started
      |failed: Failed to import project structure
      """.trimMargin()
    )
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_gradleCancelled() {
    val path = prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")
    path.resolve("settings.gradle").writeText("Thread.sleep(200); println('waiting!'); Thread.sleep(30_000)")

    val log = openProjectWithEventLogging("project", outputHandler = { output ->
      if (output.contains("waiting!")) {
        CoreProgressManager.getCurrentIndicators()
          .single { it.text.contains("Gradle:") }
          .cancel()
      }
    }) { project ->
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isTrue()
    }

    expect.that(log).startsWith(
      """
      |started
      |cancelled
      """.trimMargin()
    )
  }

  /**
   * A data service which simulates cancellation of import at data services phase.
   *
   * Note, that it needs to run first to avoid `ProcessCancelledException` related memory leaks, which are later caught by the testing
   * infrastructure. For example, see https://youtrack.jetbrains.com/issue/IDEA-298437.
   */
  @Order(BUILTIN_MODULE_DATA_SERVICE_ORDER - 1)
  class CancellingService : AbstractModuleDataService<ModuleData>() {
    override fun getTargetDataKey(): Key<ModuleData> = ProjectKeys.MODULE
    override fun importData(
      toImport: MutableCollection<out DataNode<ModuleData>>,
      projectData: ProjectData?,
      project: Project,
      modelsProvider: IdeModifiableModelsProvider
    ) {
      ProgressManager.getInstance().progressIndicator.cancel()
      ProgressManager.checkCanceled()
    }

  }

  @Suppress("UnstableApiUsage")
  @Test
  fun testCorrectSyncEventsPublished_dataImporterCancelled() {
    (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint(ProjectDataService.EP_NAME)
      .registerExtension(CancellingService(), projectRule.testRootDisposable)
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION, "project")

    val log = openProjectWithEventLogging("project")

    assertThat(log).isEqualTo(
      """
      |started
      |cancelled
      """.trimMargin()
    )
  }

  private fun openProjectWithEventLogging(
    name: String,
    outputHandler: (Project.(String) -> Unit)? = null,
    body: (Project) -> Unit = {}
  ): String {
    val completedChanged = CountDownLatch(1)
    val log = buildString {
      openPreparedProject(
        name,
        options = OpenPreparedProjectOptions(
          verifyOpened = { /* do nothing */ },
          outputHandler = outputHandler,
          subscribe = {
            it.subscribe(GRADLE_SYNC_TOPIC, object : GradleSyncListener {
              override fun syncStarted(project: Project) {
                appendLine("started")
              }

              override fun syncFailed(project: Project, errorMessage: String) {
                appendLine("failed: $errorMessage")
                completed()
              }

              override fun syncSucceeded(project: Project) {
                appendLine("succeeded")
                completed()
              }

              override fun syncSkipped(project: Project) {
                appendLine("skipped")
                completed()
              }

              override fun syncCancelled(project: Project) {
                appendLine("cancelled")
                completed()
              }

              private fun completed() {
                completedChanged.countDown()
              }
            })
          })
      ) { project ->
        completedChanged.awaitSecondsOrThrow(10)
        expect.that(GradleSyncState.getInstance(project).isSyncInProgress).isFalse()
        body(project)
      }
    }.trim()
    return log
  }

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> = emptyList()
}

fun CountDownLatch.awaitSecondsOrThrow(seconds: Long): Boolean {
  return await(seconds, TimeUnit.MINUTES)
    .also { if (!it) error("Timeout waiting for $this") }
}