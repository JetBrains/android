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
package com.android.tools.idea.gradle.project.sync.runsGradle

import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension
import com.android.tools.idea.gradle.project.sync.GRADLE_SYNC_TOPIC
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.TestExceptionModel
import com.android.tools.idea.gradle.project.sync.TestGradleModel
import com.android.tools.idea.gradle.project.sync.TestParameterizedGradleModel
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
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
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.task.ProjectTaskManager
import io.ktor.util.reflect.instanceOf
import org.jetbrains.annotations.SystemIndependent
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PlatformIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testModelBuildServiceInCompositeBuilds() {
    val compositeBuild = projectRule.prepareTestProject(TestProject.COMPOSITE_BUILD, "project")
    CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(
      CapturePlatformModelsProjectResolverExtension.TestGradleModels(),
      projectRule.testRootDisposable
    )
    compositeBuild.open { project ->
      for (module in ModuleManager.getInstance(project).modules) {
        if (ExternalSystemApiUtil.getExternalModuleType(module) == "sourceSet") continue

        val gradleTestModel: TestGradleModel? = CapturePlatformModelsProjectResolverExtension.getTestGradleModel(module)
        expect.that(gradleTestModel).named("TestGradleModel($module)").isNotNull()

        val gradleParameterizedTestModel: TestParameterizedGradleModel? =
          CapturePlatformModelsProjectResolverExtension.getTestParameterizedGradleModel(module)
        expect.that(gradleParameterizedTestModel).named("TestParameterizedGradleModel($module)").isNotNull()
        if (gradleParameterizedTestModel != null) {
          expect.that(gradleParameterizedTestModel.message).named("TestParameterizedGradleModel($module).message")
            .isEqualTo("Parameter: EHLO BuildDir: ${ExternalSystemApiUtil.getExternalProjectPath(module)}/build")
        }
      }
    }
  }

  private fun File.resolveVirtualIfCached(relativePath: String): VirtualFile? {
    val baseVirtual = VfsUtil.findFileByIoFile(this, false)
    val relativeTarget = resolve(relativePath).relativeTo(this).toPath()
    return relativeTarget.fold(baseVirtual) { acc, path ->
      (acc as? NewVirtualFile)?.findChildIfCached(path.toString())
    }
  }

  @Test
  fun `importing an already built project does not add all files to the VFS`() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")
    val root = simpleApplication.root

    // create build dir with some content, to simulate built project
    val buildDir = root.resolve("app/build").also { it.mkdirs() }
    val dexOutputDir = root.resolve("app/build/intermediates/dex/debug").also { it.mkdirs() }

    fun verifyVfsState() {
      expect.that(buildDir.exists()).isTrue()
      expect.that(root.resolveVirtualIfCached("app/build")).isNotNull()
      expect.that(dexOutputDir.exists()).isTrue()
      expect.that(root.resolveVirtualIfCached("app/build/intermediates/dex/debug")).isNull()
    }

    simpleApplication.open { _ -> verifyVfsState() }

    // Check the state is the same if the models are cached
    simpleApplication.open { project -> verifyVfsState() }
  }

  @Test
  fun testBuildOutputFoldersAreRefreshed() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")
    val root = simpleApplication.root

    simpleApplication.open { project ->
      val compilerOutputs = listOf(
        root.resolve("app/build/intermediates/javac/debug/compileDebugJavaWithJavac"),
        root.resolve("app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"),
      )
      compilerOutputs.forEach {
        assertThat(it.exists()).isFalse()  // Verify test assumptions.
      }
      ProjectTaskManager.getInstance(project).buildAllModules().blockingGet(1, TimeUnit.MINUTES)
      compilerOutputs.forEach {
        assertThat(it.exists()).isTrue()  // Verify test assumptions.

        VfsUtil.findFileByIoFile(it, false).let { vFile ->
          assertThat(vFile).isNotNull()
          assertThat(vFile!!.isValid).isTrue()
        }
      }
    }
  }

  @Test
  fun testCorrectSyncEventsPublished_successfulSync() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")
    val log = simpleApplication.openProjectWithEventLogging { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    expect.that(log).isEqualTo("""
      |started(.)
      |succeeded(.)
      |ended: SUCCESS
      """.trimMargin())
  }

  @Test
  fun testCorrectSyncEventsPublished_reopen() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")

    simpleApplication.open { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }
    val log = simpleApplication.openProjectWithEventLogging { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
    }

    expect.that(log).isEqualTo(
      """
      |skipped
      |ended: SKIPPED
      """.trimMargin()
    )
  }

  @Test
  fun testCorrectSyncEventsPublished_badConfig() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")
    simpleApplication.root.resolve("settings.gradle").writeText("***BAD FILE***")

    val log = simpleApplication.openProjectWithEventLogging { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    expect.that(log).startsWith(
      """
      |started(.)
      |failed(.):
      """.trimMargin()
    )
    expect.that(log).contains("***BAD FILE***")
    expect.that(log).endsWith(
      """
      |ended: FAILURE
      """.trimMargin()
    )
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
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")

    val log = simpleApplication.openProjectWithEventLogging { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    assertThat(log).isEqualTo(
      """
      |started(.)
      |failed(.): Failed to import project structure
      |ended: FAILURE
      """.trimMargin()
    )
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_dataImporterCrashesAfterSuccessfulOpen() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")

    val log = simpleApplication.openProjectWithEventLogging { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)

      (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint(ProjectDataService.EP_NAME)
        .registerExtension(FailingService(), projectRule.testRootDisposable)
      AndroidGradleTests.syncProject(project, GradleSyncInvoker.Request.testRequest()) {
        // Do not check status.
      }

      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isTrue()
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    assertThat(log).isEqualTo(
      """
     |started(.)
     |succeeded(.)
     |ended: SUCCESS
     |started(.)
     |failed(.): Failed to import project structure
     |ended: FAILURE
      """.trimMargin()
    )
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_gradleCancelled() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")
    val root = simpleApplication.root
    root.resolve("settings.gradle").writeText("Thread.sleep(200); println('waiting!'); Thread.sleep(30_000)")

    val log = simpleApplication.openProjectWithEventLogging(outputHandler = { output ->
      if (output.contains("waiting!")) {
        CoreProgressManager.getCurrentIndicators()
          .single { it.instanceOf(ProgressWindow::class) }
          .cancel()
      }
    }) { project ->
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isTrue()
      // Cancelling initial sync results in FAILURE to avoid blocking the UI waiting for UNKNOWN state to be gone.
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    expect.that(log).startsWith(
      """
      |started(.)
      |cancelled(.)
      """.trimMargin()
    )
    expect.that(log).endsWith(
      """
      |ended: FAILURE
      """.trimMargin()
    )
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun testCorrectSyncEventsPublished_gradleCancelledAfterSuccessfulOpen() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")
    val root = simpleApplication.root

    val log = simpleApplication.openProjectWithEventLogging(outputHandler = { output ->
      if (output.contains("waiting!")) {
        CoreProgressManager.getCurrentIndicators()
          .single { it.instanceOf(ProgressWindow::class) }
          .cancel()
      }
    }) { project ->

      root.resolve("settings.gradle").writeText("Thread.sleep(200); println('waiting!'); Thread.sleep(30_000)")
      AndroidGradleTests.syncProject(project, GradleSyncInvoker.Request.testRequest()) {
        // Do not check status.
      }

      // Cancelling sync does not change the current state.
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isFalse()
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    expect.that(log).startsWith(
      """
      |started(.)
      |succeeded(.)
      |ended: SUCCESS
      |started(.)
      |cancelled(.)
      |ended: SUCCESS
      """.trimMargin()
    )
  }

  /**
   * A data service which simulates cancellation of import at data services phase.
   *
   * Note, that it needs to run first to avoid `ProcessCanceledException` related memory leaks, which are later caught by the testing
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
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")

    val log = simpleApplication.openProjectWithEventLogging { project ->
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isTrue()
      // Cancelling initial sync results in FAILURE to avoid blocking the UI waiting for UNKNOWN state to be gone.
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.FAILURE)
    }

    assertThat(log).isEqualTo(
      """
      |started(.)
      |cancelled(.)
      |ended: FAILURE
      """.trimMargin()
    )
  }

  @Suppress("UnstableApiUsage")
  @Test
  fun testCorrectSyncEventsPublished_dataImporterCancelledAfterSuccessfulOpen() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")

    val log = simpleApplication.openProjectWithEventLogging { project ->
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isFalse()
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)

      (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint(ProjectDataService.EP_NAME)
        .registerExtension(CancellingService(), projectRule.testRootDisposable)

      AndroidGradleTests.syncProject(project, GradleSyncInvoker.Request.testRequest()) {
        // Do not check status.
      }

      // Cancelling sync does not change the current state.
      expect.that(GradleSyncState.getInstance(project).lastSyncFailed()).isFalse()
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    assertThat(log).isEqualTo(
      """
      |started(.)
      |succeeded(.)
      |ended: SUCCESS
      |started(.)
      |cancelled(.)
      |ended: SUCCESS
      """.trimMargin()
    )
  }

  @Test
  fun testSimpleApplicationNotAtRoot() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT)
    val log = preparedProject.openProjectWithEventLogging { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    expect.that(log).isEqualTo("""
      |started(gradle_project)
      |succeeded(gradle_project)
      |ended: SUCCESS
      """.trimMargin())
  }

  @Test
  fun testSimpleApplicationMultipleRoots() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS)
    val log = preparedProject.openProjectWithEventLogging { project ->
      expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
    }

    expect.that(log).isEqualTo("""
      |started(gradle_project_1)
      |succeeded(gradle_project_1)
      |ended: SUCCESS
      |started(gradle_project_2)
      |succeeded(gradle_project_2)
      |ended: SUCCESS
      """.trimMargin())
  }

  @Test
  fun testSimpleApplicationReopened() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    run {
      val log = preparedProject.openProjectWithEventLogging { project ->
        expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      }

      expect.that(log).isEqualTo(
        """
      |started(.)
      |succeeded(.)
      |ended: SUCCESS
      """.trimMargin()
      )
    }

    run {
      val log = preparedProject.openProjectWithEventLogging { project ->
        expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      }

      expect.that(log).isEqualTo(
        """
      |skipped
      |ended: SKIPPED
      """.trimMargin()
      )
    }
  }

  @Test
  fun testGradleProjectWithoutAndroidReopened() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    preparedProject.root.resolve("settings.gradle").writeText("// this build only has a root subproject")

    run {
      val log = preparedProject.openProjectWithEventLogging { project ->
        expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SUCCESS)
      }

      expect.that(log).isEqualTo(
        """
      |started(.)
      |succeeded(.)
      |ended: SUCCESS
      """.trimMargin()
      )
    }

    run {
      val log = preparedProject.openProjectWithEventLogging { project ->
        expect.that(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED)
      }

      expect.that(log).isEqualTo(
        """
      |skipped
      |ended: SKIPPED
      """.trimMargin()
      )
    }
  }

  @Test
  fun `exceptions can be deserialized`() {
    val preparedProject =
      projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION)
    CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(
      CapturePlatformModelsProjectResolverExtension.TestExceptionModels(),
      projectRule.testRootDisposable
    )
    preparedProject.open { project ->
      for (module in ModuleManager.getInstance(project).modules) {
        if (ExternalSystemApiUtil.getExternalModuleType(module) == "sourceSet") continue

        val exceptionModel: TestExceptionModel? = CapturePlatformModelsProjectResolverExtension.getTestExceptionModel(module)
        expect.that(exceptionModel).isNotNull()
        expect.that(exceptionModel?.exception as? IllegalStateException).isNotNull()
        expect.that((exceptionModel?.exception as? IllegalStateException)?.message).isEqualTo("expected error")
        expect.that((exceptionModel?.exception as? IllegalStateException)?.stackTrace).isNotEmpty()
      }
    }
  }

  private fun PreparedTestProject.openProjectWithEventLogging(
    outputHandler: (Project.(String) -> Unit)? = null,
    body: (Project) -> Unit = {}
  ): String {
    val root = root

    fun String.toLocalPath(): String = File(this).relativeToOrSelf(root).path.takeUnless { it.isEmpty() } ?: "."

    val completedChanged = CountDownLatch(1)
    val log = buildString {
      open(
        updateOptions = {
          it.copy(
            verifyOpened = { /* do nothing */ },
            outputHandler = outputHandler,
            subscribe = {
              it.subscribe(GRADLE_SYNC_TOPIC, object : GradleSyncListenerWithRoot {
                override fun syncStarted(project: Project, rootProjectPath: @SystemIndependent String) {
                  appendLine("started(${rootProjectPath.toLocalPath()})")
                }

                override fun syncFailed(project: Project, errorMessage: String, rootProjectPath: @SystemIndependent String) {
                  appendLine("failed(${rootProjectPath.toLocalPath()}): $errorMessage")
                }

                override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
                  appendLine("succeeded(${rootProjectPath.toLocalPath()})")
                }

                override fun syncSkipped(project: Project) {
                  appendLine("skipped")
                }

                override fun syncCancelled(project: Project, rootProjectPath: @SystemIndependent String) {
                  appendLine("cancelled(${rootProjectPath.toLocalPath()})")
                }
              })
              it.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : ProjectSystemSyncManager.SyncResultListener {
                override fun syncEnded(result: SyncResult) {
                  appendLine("ended: $result")
                  completedChanged.countDown()
                }
              })
            }
          )
        }
      ) { project ->
        // When sync is cancelled, and it is detected by handling `FinishBuildEvent` with `SuccessResult` the `syncCancelled` event might be
        // delivered after we reach this point.
        completedChanged.awaitSecondsOrThrow(10)
        expect.that(GradleSyncState.getInstance(project).isSyncInProgress).isFalse()
        body(project)
      }
    }.trim()
    return log
  }
}

fun CountDownLatch.awaitSecondsOrThrow(seconds: Long): Boolean {
  return await(seconds, TimeUnit.SECONDS)
    .also { if (!it) error("Timeout waiting for $this") }
}
