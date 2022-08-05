/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleUtil
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.Executor

interface GradleBuildInvoker {
  fun cleanProject(): ListenableFuture<GradleMultiInvocationResult>

  fun generateSources(modules: Array<Module>): ListenableFuture<GradleMultiInvocationResult>
  fun compileJava(modules: Array<Module>, testCompileType: TestCompileType): ListenableFuture<GradleMultiInvocationResult>
  fun assemble(testCompileType: TestCompileType): ListenableFuture<AssembleInvocationResult>
  fun assemble(modules: Array<Module>, testCompileType: TestCompileType): ListenableFuture<AssembleInvocationResult>
  fun bundle(modules: Array<Module>): ListenableFuture<AssembleInvocationResult>

  fun rebuild(): ListenableFuture<GradleMultiInvocationResult>
  fun rebuildWithTempOptions(rootProjectPath: File, options: List<String>): ListenableFuture<GradleMultiInvocationResult>

  /**
   * Executes Gradle tasks requested in each request in separate Gradle invocations (in parallel or sequentially and in arbitrary order).
   * The results (including failed sub-builds) are reported as [GradleInvocationResult]s wrapped into [AssembleInvocationResult], however,
   * any unexpected failures are returned as a failed future. The order of invocations in the [AssembleInvocationResult] matches the order
   * of requests in [request].
   *
   * Note, the build mode of all requests need to be the same. If a build request is not intended to be used in deployment, [executeTasks]
   * can run arbitrary requests without this restriction.
   */
  fun executeAssembleTasks(assembledModules: Array<Module>, request: List<Request>): ListenableFuture<AssembleInvocationResult>

  /**
   * Executes build requests in separate Gradle invocations (in parallel or sequentially and in arbitrary order).
   * The results (including failed sub-builds) are reported as [GradleInvocationResult]s wrapped into [GradleMultiInvocationResult], however,
   * any unexpected failures are returned as a failed future. The order of invocations in the [GradleMultiInvocationResult] matches the order
   * of requests in [request].
   */
  fun executeTasks(request: List<Request>): ListenableFuture<GradleMultiInvocationResult>

  /**
   * Executes one build request. The result (including a failed build) is reported as [GradleInvocationResult], however any unexpected
   * failures are reported as a failed future.
   */
  fun executeTasks(request: Request): ListenableFuture<GradleInvocationResult>

  fun stopBuild(id: ExternalSystemTaskId): Boolean
  val project: Project

  data class Request constructor(
    val mode: BuildMode?,
    val project: Project,
    val rootProjectPath: File,
    val gradleTasks: List<String>,
    val taskId: ExternalSystemTaskId,
    val jvmArguments: List<String> = emptyList(),
    val commandLineArguments: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val isPassParentEnvs: Boolean = true,
    val isWaitForCompletion: Boolean = false,

    /**
     * If true, the build output window will not automatically be shown on failure.
     */
    val doNotShowBuildOutputOnFailure: Boolean = false,
    val listener: ExternalSystemTaskNotificationListener? = null
  ) {
    companion object {
      @JvmStatic
      fun builder(
        project: Project,
        rootProjectPath: File,
        vararg gradleTasks: String
      ): Builder = Builder(project, rootProjectPath, gradleTasks.toList())

      @JvmStatic
      fun builder(
        project: Project,
        rootProjectPath: File,
        gradleTasks: Collection<String>
      ): Builder = Builder(project, rootProjectPath, gradleTasks.toList())

      @JvmStatic
      fun copyRequest(request: Request): Request =
        request.copy(taskId = ExternalSystemTaskId.create(GradleUtil.GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, request.project))
    }

    class Builder constructor(
      project: Project,
      rootProjectPath: File,
      gradleTasks: List<String>
    ) {
      private var request: Request = Request(
        mode = null,
        project = project,
        rootProjectPath = rootProjectPath,
        gradleTasks = gradleTasks,
        taskId = ExternalSystemTaskId.create(GradleUtil.GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
      )

      fun setMode(value: BuildMode?): Builder {
        request = request.copy(mode = value)
        return this
      }

      fun setTaskId(value: ExternalSystemTaskId): Builder {
        request = request.copy(taskId = value)
        return this
      }

      fun setJvmArguments(value: List<String>): Builder {
        request = request.copy(jvmArguments = value.toList())
        return this
      }

      fun setCommandLineArguments(value: List<String>): Builder {
        request = request.copy(commandLineArguments = value.toList())
        return this
      }

      fun withEnvironmentVariables(value: Map<String, String>): Builder {
        request = request.copy(env = request.env + value)
        return this
      }

      fun passParentEnvs(value: Boolean): Builder {
        request = request.copy(isPassParentEnvs = value)
        return this
      }

      fun waitForCompletion(): Builder {
        request = request.copy(isWaitForCompletion = true)
        return this
      }

      fun setDoNotShowBuildOutputOnFailure(value: Boolean): Builder {
        request = request.copy(doNotShowBuildOutputOnFailure = value)
        return this
      }

      fun setListener(value: ExternalSystemTaskNotificationListener?): Builder {
        request = request.copy(listener = value)
        return this
      }

      fun build(): Request = request
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GradleBuildInvoker {
      return project.getService(GradleBuildInvoker::class.java)
    }
  }

  @Deprecated("This property does not return anything useful as its state can change at any moment. It should not be used.")
  val internalIsBuildRunning: Boolean
}

fun <T : GradleBuildResult> ListenableFuture<T>.whenFinished(executor: Executor, handler: (T) -> Unit) {
  addCallback(executor, { handler(it!!) }, { throw it!! })
}