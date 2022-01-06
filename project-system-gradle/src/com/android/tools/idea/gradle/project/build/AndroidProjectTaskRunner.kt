package com.android.tools.idea.gradle.project.build

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Companion.getInstance
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Request.Companion.builder
import com.android.tools.idea.gradle.project.build.invoker.GradleTaskFinder
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.java.JavaFacet
import com.android.tools.idea.gradle.util.BuildMode
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskNotification
import com.intellij.task.ProjectTaskResult
import com.intellij.task.ProjectTaskRunner
import java.util.concurrent.atomic.AtomicInteger

class AndroidProjectTaskRunner : ProjectTaskRunner() {
  override fun run(
    project: Project,
    context: ProjectTaskContext,
    callback: ProjectTaskNotification?,
    tasks: Collection<ProjectTask?>
  ) {
    val moduleBuildTasksMap =
      tasks.filterIsInstance<ModuleBuildTask>()
      .groupBy { task: ModuleBuildTask -> task.isIncrementalBuild }
    val aggregatedCallback = callback?.let { MergedProjectTaskNotification(callback, 2) }
    executeTasks(project, BuildMode.REBUILD, moduleBuildTasksMap[false].orEmpty(), aggregatedCallback)
    executeTasks(project, BuildMode.COMPILE_JAVA, moduleBuildTasksMap[true].orEmpty(), aggregatedCallback)
  }

  override fun canRun(projectTask: ProjectTask): Boolean {
    if (projectTask !is ModuleBuildTask) return false
    val module = projectTask.module
    return GradleFacet.getInstance(module) != null || JavaFacet.getInstance(module) != null /* || AndroidModuleModel.get(module) != null*/
  }

  private fun executeTasks(
    project: Project,
    buildMode: BuildMode,
    moduleBuildTasks: List<ModuleBuildTask>,
    callback: ProjectTaskNotification?
  ) {
    val modules = moduleBuildTasks.map { task: ModuleBuildTask -> task.module }
    if (modules.isEmpty()) {
      // nothing to build
      callback?.finished(ProjectTaskResult(false, 0, 0))
      return
    }
    val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(modules[0])
    if (rootProjectPath == null) {
      callback?.finished(ProjectTaskResult(false, 1, 0))
      return
    }
    val tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules.toTypedArray(), buildMode, TestCompileType.ALL)
    val gradleBuildInvoker = getInstance(project)
    val rootPaths = tasks.keys().elementSet()
    if (rootPaths.isEmpty()) {
      callback?.finished(ProjectTaskResult(false, 1, 0))
      return
    }
    val aggregatedCallback: ProjectTaskNotification? =
      if (callback == null) null else MergedProjectTaskNotification(callback, rootPaths.size)
    for (projectRootPath in rootPaths) {
      // the blocking mode required because of static behaviour of the BuildSettings.setBuildMode() method
      val listenerDelegate: ExternalSystemTaskNotificationListener? =
        if (aggregatedCallback == null) null else object : ExternalSystemTaskNotificationListenerAdapter(null) {
          override fun onSuccess(id: ExternalSystemTaskId) {
            super.onSuccess(id)
            aggregatedCallback.finished(ProjectTaskResult(false, 0, 0))
          }

          override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
            super.onFailure(id, e)
            aggregatedCallback.finished(ProjectTaskResult(false, 1, 0))
          }

          override fun onCancel(id: ExternalSystemTaskId) {
            super.onCancel(id)
            aggregatedCallback.finished(ProjectTaskResult(true, 0, 0))
          }
        }
      val request = builder(project, projectRootPath.toFile(), tasks[projectRootPath])
        .waitForCompletion()
        .setListener(listenerDelegate)
        .build()
      gradleBuildInvoker.executeTasks(request)
    }
  }

  private class MergedProjectTaskNotification(
    private val myCallback: ProjectTaskNotification,
    private val myExpectedResults: Int
  ) : ProjectTaskNotification {
    private val myResultsCounter = AtomicInteger(0)
    private var myAborted = false
    private var myErrors = 0
    private var myWarnings = 0
    override fun finished(executionResult: ProjectTaskResult) {
      val finished = myResultsCounter.incrementAndGet()
      if (executionResult.isAborted) {
        myAborted = true
      }
      myErrors += executionResult.errors
      myWarnings += executionResult.warnings
      if (finished == myExpectedResults) {
        myCallback.finished(ProjectTaskResult(myAborted, myErrors, myWarnings))
      }
    }
  }
}