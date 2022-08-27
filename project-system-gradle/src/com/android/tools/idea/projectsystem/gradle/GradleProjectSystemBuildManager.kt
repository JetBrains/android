package com.android.tools.idea.projectsystem.gradle

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.isAndroidTestFile
import com.android.tools.idea.projectsystem.isUnitTestFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import java.util.concurrent.atomic.AtomicInteger

private fun BuildStatus.toProjectSystemBuildStatus(): ProjectSystemBuildManager.BuildStatus = when(this) {
  BuildStatus.SUCCESS -> ProjectSystemBuildManager.BuildStatus.SUCCESS
  BuildStatus.FAILED -> ProjectSystemBuildManager.BuildStatus.FAILED
  BuildStatus.CANCELED -> ProjectSystemBuildManager.BuildStatus.CANCELLED
}

private fun BuildMode.toProjectSystemBuildMode(): ProjectSystemBuildManager.BuildMode = when(this) {
  BuildMode.CLEAN -> ProjectSystemBuildManager.BuildMode.CLEAN
  BuildMode.COMPILE_JAVA -> ProjectSystemBuildManager.BuildMode.COMPILE
  BuildMode.ASSEMBLE -> ProjectSystemBuildManager.BuildMode.ASSEMBLE
  BuildMode.REBUILD -> ProjectSystemBuildManager.BuildMode.ASSEMBLE
  BuildMode.SOURCE_GEN -> ProjectSystemBuildManager.BuildMode.UNKNOWN
  BuildMode.BUNDLE -> ProjectSystemBuildManager.BuildMode.ASSEMBLE
  BuildMode.APK_FROM_BUNDLE -> ProjectSystemBuildManager.BuildMode.ASSEMBLE
}

@Service
private class GradleProjectSystemBuildPublisher(val project: Project): GradleBuildListener.Adapter(), Disposable {
  private val PROJECT_SYSTEM_BUILD_TOPIC = Topic("Project build", ProjectSystemBuildManager.BuildListener::class.java)

  /**
   * The counter is only supposed to be called on UI thread therefore it does not require any lock/synchronization.
   */
  private var buildCount = 0

  init {
    GradleBuildState.subscribe(project, this, this)
  }

  override fun buildStarted(context: BuildContext) {
    buildCount++
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC)
      .buildStarted(context.buildMode?.toProjectSystemBuildMode() ?: ProjectSystemBuildManager.BuildMode.UNKNOWN)
  }

  override fun buildFinished(status: BuildStatus, context: BuildContext?) {
    val result = ProjectSystemBuildManager.BuildResult(
      context?.buildMode?.toProjectSystemBuildMode() ?: ProjectSystemBuildManager.BuildMode.UNKNOWN,
      status.toProjectSystemBuildStatus(),
      System.currentTimeMillis())
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC).beforeBuildCompleted(result)
    buildCount = maxOf(buildCount - 1, 0)
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC).buildCompleted(result)
  }

  override fun dispose() {
  }

  fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) =
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_SYSTEM_BUILD_TOPIC, buildListener)

  val isBuilding: Boolean
    get() = buildCount > 0
}

class GradleProjectSystemBuildManager(val project: Project): ProjectSystemBuildManager {
  init {
    // TODO(b/237224221): Rework this
    // Creating the publisher straight away so that it can keep track of all the builds
    project.getService(GradleProjectSystemBuildPublisher::class.java)
  }
  override fun compileProject() {
    val modules = ModuleManager.getInstance(project).modules
    GradleBuildInvoker.getInstance(project).compileJava(modules, TestCompileType.ALL)
  }

  override fun compileFilesAndDependencies(files: Collection<VirtualFile>) {
    val modules = files.mapNotNull { ModuleUtil.findModuleForFile(it, project) }.toSet()
    GradleBuildInvoker.getInstance(project).compileJava(modules.toTypedArray(), getTestCompileType(files))
  }

  override fun getLastBuildResult(): ProjectSystemBuildManager.BuildResult =
    GradleBuildState.getInstance(project).lastFinishedBuildSummary?.let {
      ProjectSystemBuildManager.BuildResult(
        it.context?.buildMode?.toProjectSystemBuildMode() ?: ProjectSystemBuildManager.BuildMode.UNKNOWN,
        it.status.toProjectSystemBuildStatus(),
        System.currentTimeMillis())
    } ?: ProjectSystemBuildManager.BuildResult.createUnknownBuildResult()

  override fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) =
    project.getService(GradleProjectSystemBuildPublisher::class.java).addBuildListener(parentDisposable, buildListener)

  /**
   * To ensure the accuracy this should be called on the UI thread to be naturally serialized with BuildListener callbacks.
   */
  @get:UiThread
  override val isBuilding: Boolean
    get() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      return project.getService(GradleProjectSystemBuildPublisher::class.java).isBuilding
    }

  private fun getTestCompileType(files: Collection<VirtualFile>): TestCompileType {
    var haveUnitTestFiles = false
    var haveAndroidTestFiles = false

    for (file in files) {
      haveAndroidTestFiles = haveAndroidTestFiles || isAndroidTestFile(project, file)
      haveUnitTestFiles = haveUnitTestFiles || isUnitTestFile(project, file)
      if (haveUnitTestFiles && haveAndroidTestFiles) return TestCompileType.ALL
    }

    if (haveUnitTestFiles) return TestCompileType.UNIT_TESTS
    if (haveAndroidTestFiles) return TestCompileType.ANDROID_TESTS
    return TestCompileType.NONE
  }
}
