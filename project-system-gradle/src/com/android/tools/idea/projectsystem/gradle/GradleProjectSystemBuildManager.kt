package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

private fun BuildStatus.toProjectSystemBuildStatus(): ProjectSystemBuildManager.BuildStatus = when(this) {
  BuildStatus.SUCCESS -> ProjectSystemBuildManager.BuildStatus.SUCCESS
  BuildStatus.FAILED -> ProjectSystemBuildManager.BuildStatus.FAILED
  BuildStatus.CANCELED -> ProjectSystemBuildManager.BuildStatus.CANCELLED
  else -> ProjectSystemBuildManager.BuildStatus.UNKNOWN
}

private fun BuildMode?.toProjectSystemBuildMode(): ProjectSystemBuildManager.BuildMode = when(this) {
  BuildMode.CLEAN -> ProjectSystemBuildManager.BuildMode.CLEAN
  BuildMode.COMPILE_JAVA -> ProjectSystemBuildManager.BuildMode.COMPILE
  BuildMode.ASSEMBLE -> ProjectSystemBuildManager.BuildMode.ASSEMBLE
  BuildMode.REBUILD -> ProjectSystemBuildManager.BuildMode.COMPILE
  else -> ProjectSystemBuildManager.BuildMode.UNKNOWN
}

@Service
private class GradleProjectSystemBuildPublisher(val project: Project): GradleBuildListener.Adapter(), Disposable {
  private val PROJECT_SYSTEM_BUILD_TOPIC = Topic("Project build", ProjectSystemBuildManager.BuildListener::class.java)

  init {
    GradleBuildState.subscribe(project, this, this)
  }

  override fun buildStarted(context: BuildContext) {
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC).buildStarted(context.buildMode.toProjectSystemBuildMode())
  }

  override fun buildFinished(status: BuildStatus, context: BuildContext?) {
    val result = ProjectSystemBuildManager.BuildResult(
      context?.buildMode.toProjectSystemBuildMode(),
      status.toProjectSystemBuildStatus(),
      System.currentTimeMillis())
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC).beforeBuildCompleted(result)
    project.messageBus.syncPublisher(PROJECT_SYSTEM_BUILD_TOPIC).buildCompleted(result)
  }

  override fun dispose() {
  }

  fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) =
    project.messageBus.connect(parentDisposable).subscribe(PROJECT_SYSTEM_BUILD_TOPIC, buildListener)
}

class GradleProjectSystemBuildManager(val project: Project): ProjectSystemBuildManager {
  override fun compileProject() {
    GradleProjectBuilder.getInstance(project).compileJava()
  }

  override fun getLastBuildResult(): ProjectSystemBuildManager.BuildResult =
    GradleBuildState.getInstance(project).summary?.let {
      ProjectSystemBuildManager.BuildResult(
        it.context?.buildMode.toProjectSystemBuildMode(),
        it.status.toProjectSystemBuildStatus(),
        System.currentTimeMillis())
    } ?: ProjectSystemBuildManager.BuildResult.createUnknownBuildResult()

  override fun addBuildListener(parentDisposable: Disposable, buildListener: ProjectSystemBuildManager.BuildListener) =
    project.getService(GradleProjectSystemBuildPublisher::class.java).addBuildListener(parentDisposable, buildListener)
}