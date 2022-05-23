package com.android.tools.idea.gradle.project.build

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleMultiInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.GradleTaskFinder
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.isAndroidProject
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.task.ModuleBuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.TaskRunnerResults
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

class AndroidProjectTaskRunner : ProjectTaskRunner() {
  private val isAndroidStudio = IdeInfo.getInstance().isAndroidStudio
  override fun run(project: Project, context: ProjectTaskContext, vararg tasks: ProjectTask): Promise<Result> {
    return executeTasks(project, tasks.filterIsInstance<ModuleBuildTask>())
  }

  override fun canRun(projectTask: ProjectTask): Boolean {
    return projectTask is ModuleBuildTask &&
      (isAndroidStudio || projectTask.module.project.isAndroidProject) &&
      ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, projectTask.module)
  }

  @Suppress("UnstableApiUsage")
  private fun executeTasks(
    project: Project,
    tasks: List<ModuleBuildTask>
  ): Promise<Result> {
    val taskFinder = GradleTaskFinder.getInstance()
    val gradleBuildInvoker = GradleBuildInvoker.getInstance(project)

    data class TaskGroup(val buildMode: BuildMode, val gradleProjectRoot: Path)

    fun ModuleBuildTask.getBuildMode() = if (isIncrementalBuild) BuildMode.COMPILE_JAVA else BuildMode.REBUILD

    fun findTasks(buildMode: BuildMode, modules: List<Module>): Map<TaskGroup, Collection<String>> {
      return taskFinder
        .findTasksToExecute(modules.toTypedArray(), buildMode, TestCompileType.ALL)
        .asMap()
        .mapKeys { TaskGroup(buildMode, it.key) }
    }

    val tasksGroups: Map<TaskGroup, Collection<String>> =
      tasks
        .groupBy({ it.getBuildMode() }, { it.module })
        .map { (buildMode, modules) -> findTasks(buildMode, modules) }
        .reduce { a, b -> a + b }

    if (tasksGroups.keys.isEmpty()) {
      return resolvedPromise(TaskRunnerResults.FAILURE)
    }

    val requests =
      tasksGroups
        .map { (taskGroup, tasks) ->
          GradleBuildInvoker.Request
            .builder(project, taskGroup.gradleProjectRoot.toFile(), tasks)
            .setMode(taskGroup.buildMode)
            .build()
        }

    return gradleBuildInvoker.executeTasks(requests).toPromise { it.toTaskRunnerResults() }
  }
}

@Suppress("UnstableApiUsage")
private fun GradleMultiInvocationResult.toTaskRunnerResults(): ProjectTaskRunner.Result {
  return when {
    isBuildCancelled -> TaskRunnerResults.ABORTED
    isBuildSuccessful -> TaskRunnerResults.SUCCESS
    else -> TaskRunnerResults.FAILURE
  }
}

private fun <T: Any, R> ListenableFuture<T>.toPromise(transform: (T) -> R): AsyncPromise<R> {
  val result = AsyncPromise<R>()
  this.addCallback(
    directExecutor(),
    success = { invocationResult ->
      result.setResult(invocationResult?.let(transform))
    },
    failure = { result.setError(it ?: error("Unknown error")) }
  )
  return result
}
