package com.android.tools.idea.projectsystem.gradle

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_BUILD_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.atomic.AtomicInteger

private fun BuildStatus.toProjectSystemBuildStatus(): ProjectSystemBuildManager.BuildStatus = when(this) {
  BuildStatus.SUCCESS -> ProjectSystemBuildManager.BuildStatus.SUCCESS
  BuildStatus.FAILED -> ProjectSystemBuildManager.BuildStatus.FAILED
  BuildStatus.CANCELED -> ProjectSystemBuildManager.BuildStatus.CANCELLED
}

private fun BuildMode.toProjectSystemBuildMode(): ProjectSystemBuildManager.BuildMode = when(this) {
  BuildMode.CLEAN -> ProjectSystemBuildManager.BuildMode.CLEAN
  BuildMode.COMPILE_JAVA -> ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE
  BuildMode.ASSEMBLE -> ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE
  BuildMode.REBUILD -> ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE
  BuildMode.SOURCE_GEN -> ProjectSystemBuildManager.BuildMode.UNKNOWN
  BuildMode.BUNDLE -> ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE
  BuildMode.APK_FROM_BUNDLE -> ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE
  BuildMode.BASELINE_PROFILE_GEN -> ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE
  BuildMode.BASELINE_PROFILE_GEN_ALL_VARIANTS -> ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE
}

@Service
private class GradleProjectSystemBuildPublisher(val project: Project): GradleBuildListener.Adapter(), Disposable {
  /**
   * The counter is only supposed to be called on UI thread therefore it does not require any lock/synchronization.
   */
  private var buildCount = AtomicInteger(0)

  init {
    GradleBuildState.subscribe(project, this, this)
  }

  @UiThread
  override fun buildStarted(context: BuildContext) {
    buildCount.incrementAndGet()
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC)
      .buildStarted(context.buildMode?.toProjectSystemBuildMode() ?: ProjectSystemBuildManager.BuildMode.UNKNOWN)
  }

  @UiThread
  override fun buildFinished(status: BuildStatus, context: BuildContext) {
    val result = ProjectSystemBuildManager.BuildResult(
      context.buildMode?.toProjectSystemBuildMode() ?: ProjectSystemBuildManager.BuildMode.UNKNOWN,
      status.toProjectSystemBuildStatus()
    )
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC).beforeBuildCompleted(result)
    buildCount.updateAndGet {
      maxOf(it - 1, 0)
    }
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC).buildCompleted(result)
  }

  override fun dispose() {
  }

  fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) =
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_SYSTEM_BUILD_TOPIC, buildListener)

  val isBuilding: Boolean
    get() = buildCount.get() > 0
}

class GradleProjectSystemBuildManager(val project: Project): ProjectSystemBuildManager {
  init {
    // TODO(b/237224221): Rework this
    // Creating the publisher straight away so that it can keep track of all the builds
    project.getService(GradleProjectSystemBuildPublisher::class.java)
  }
  override fun compileProject() {
    val modules = ModuleManager.getInstance(project).modules
    GradleBuildInvoker.getInstance(project).compileJava(modules)
  }

  override fun compileFilesAndDependencies(files: Collection<VirtualFile>) {
    val modules = files.mapNotNull { ModuleUtil.findModuleForFile(it, project) }.toSet()
    GradleBuildInvoker.getInstance(project).compileJava(modules.toTypedArray())
  }

  override fun getLastBuildResult(): ProjectSystemBuildManager.BuildResult =
    GradleBuildState.getInstance(project).lastFinishedBuildSummary?.let {
      ProjectSystemBuildManager.BuildResult(
        it.context?.buildMode?.toProjectSystemBuildMode() ?: ProjectSystemBuildManager.BuildMode.UNKNOWN,
        it.status.toProjectSystemBuildStatus()
      )
    } ?: ProjectSystemBuildManager.BuildResult.createUnknownBuildResult()

  override fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) =
    project.getService(GradleProjectSystemBuildPublisher::class.java).addBuildListener(parentDisposable, buildListener)

  /**
   * To ensure the accuracy this should be called on the UI thread to be naturally serialized with BuildListener callbacks.
   */
  override val isBuilding: Boolean
    get() {
      return project.getService(GradleProjectSystemBuildPublisher::class.java).isBuilding
    }
}
