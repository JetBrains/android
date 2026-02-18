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
import com.android.tools.idea.gradle.task.ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.Executor
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

interface GradleBuildInvoker {
  fun buildConfiguration(modules: Array<Module>, deployApkFromBundle: Boolean): ListenableFuture<AssembleInvocationResult>

  fun cleanProject(): ListenableFuture<GradleMultiInvocationResult>

  fun generateSources(modules: Array<Module>): ListenableFuture<GradleMultiInvocationResult>

  fun compileJava(modules: Array<Module>): ListenableFuture<GradleMultiInvocationResult>

  fun compileJava(): ListenableFuture<GradleMultiInvocationResult>

  fun assembleWithTests(): ListenableFuture<AssembleInvocationResult>

  fun assemble(): ListenableFuture<AssembleInvocationResult>

  fun assemble(modules: Array<Module>): ListenableFuture<AssembleInvocationResult>

  fun bundle(modules: Array<Module>): ListenableFuture<AssembleInvocationResult>

  fun rebuild(): ListenableFuture<GradleMultiInvocationResult>

  fun rebuildWithTempOptions(rootProjectPath: File, options: List<String>): ListenableFuture<GradleMultiInvocationResult>

  fun generateBaselineProfileSources(
    taskId: ExternalSystemTaskId,
    modules: Array<Module>,
    envVariables: Map<String, String>,
    args: List<String>,
    generateAllVariants: Boolean,
  ): ListenableFuture<GradleMultiInvocationResult>

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
   * Executes build requests in separate Gradle invocations (in parallel or sequentially and in arbitrary order). The results (including
   * failed sub-builds) are reported as [GradleInvocationResult]s wrapped into [GradleMultiInvocationResult], however, any unexpected
   * failures are returned as a failed future. The order of invocations in the [GradleMultiInvocationResult] matches the order of requests
   * in [request].
   */
  fun executeTasks(request: List<Request>): ListenableFuture<GradleMultiInvocationResult>

  /**
   * Executes one build request. The result (including a failed build) is reported as [GradleInvocationResult], however any unexpected
   * failures are reported as a failed future.
   */
  fun executeTasks(request: Request): ListenableFuture<GradleInvocationResult>

  fun stopBuild(id: ExternalSystemTaskId): Boolean

  val project: Project

  interface Request {
    val project: Project
    val taskId: ExternalSystemTaskId
    val mode: BuildMode?
    val data: RequestData
    val rootProjectPath: File
    val gradleTasks: List<String>
    val jvmArguments: List<String>
    val commandLineArguments: List<String>
    val env: Map<String, String>
    val isPassParentEnvs: Boolean
    val isWaitForCompletion: Boolean

    /** If true, the build output window will not automatically be shown on failure. */
    val doNotShowBuildOutputOnFailure: Boolean
    val listener: ExternalSystemTaskNotificationListener?
    val executionEnvironment: ExecutionEnvironment?

    /** Convert this request to GradleExecutionSettings to be used for Gradle Build execution. */
    fun toExecutionSettings(): GradleExecutionSettings

    /** Creates exact copy of the request with new task id so that we can run it again. */
    fun copyRequest(): Request

    /**
     * This implementation of the request must be used when there are existing GradleExecutionSettings that should be used further to invoke
     * Gradle Build. Essentially it is a wrapper around these existing GradleExecutionSettings.
     */
    private class ExecutionSettingsBasedRequestImpl(
      override val project: Project,
      override val taskId: ExternalSystemTaskId,
      override val mode: BuildMode?,
      override val rootProjectPath: File,
      executionSettings: GradleExecutionSettings,
      override val isWaitForCompletion: Boolean = false,
      override val listener: ExternalSystemTaskNotificationListener? = null,
      override val executionEnvironment: ExecutionEnvironment? = null,
    ) : Request {
      private val executionSettings = GradleExecutionSettings(executionSettings)

      override val gradleTasks: List<String>
        get() = executionSettings.tasks

      override val jvmArguments: List<String>
        get() = executionSettings.jvmArguments

      override val commandLineArguments: List<String>
        get() = executionSettings.arguments

      override val env: Map<String, String>
        get() = executionSettings.env

      override val isPassParentEnvs: Boolean
        get() = executionSettings.isPassParentEnvs

      override val doNotShowBuildOutputOnFailure: Boolean
        @Suppress("DEPRECATION")
        get() = executionSettings.getUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE) == true

      override val data: RequestData
        get() = RequestData(mode, rootProjectPath, gradleTasks, jvmArguments, commandLineArguments, env, isPassParentEnvs)

      override fun copyRequest(): Request {
        val newId = ExternalSystemTaskId.create(GradleProjectSystemUtil.GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
        return ExecutionSettingsBasedRequestImpl(
          project,
          newId,
          mode,
          rootProjectPath,
          executionSettings,
          isWaitForCompletion,
          listener,
          executionEnvironment,
        )
      }

      override fun toExecutionSettings() = GradleExecutionSettings(executionSettings)
    }

    private data class RequestImpl(
      override val project: Project,
      override val taskId: ExternalSystemTaskId,
      override val data: RequestData,
      override val isWaitForCompletion: Boolean = false,

      /** If true, the build output window will not automatically be shown on failure. */
      override val doNotShowBuildOutputOnFailure: Boolean = false,
      override val listener: ExternalSystemTaskNotificationListener? = null,
      override val executionEnvironment: ExecutionEnvironment? = null,
    ) : Request {
      override val mode: BuildMode?
        get() = data.mode

      override val rootProjectPath: File
        get() = data.rootProjectPath

      override val gradleTasks: List<String>
        get() = data.gradleTasks

      override val jvmArguments: List<String>
        get() = data.jvmArguments

      override val commandLineArguments: List<String>
        get() = data.commandLineArguments

      override val env: Map<String, String>
        get() = data.env

      override val isPassParentEnvs: Boolean
        get() = data.isPassParentEnvs

      override fun toExecutionSettings(): GradleExecutionSettings {
        return GradleProjectSystemUtil.getOrCreateGradleExecutionSettings(project).apply {
          this.tasks = data.gradleTasks
          this.withVmOptions(data.jvmArguments)
            .withArguments(data.commandLineArguments)
            .withEnvironmentVariables(data.env)
            .passParentEnvs(data.isPassParentEnvs)
        }
      }

      override fun copyRequest(): Request =
        this.copy(
          taskId = ExternalSystemTaskId.create(GradleProjectSystemUtil.GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
        )

      constructor(
        mode: BuildMode?,
        project: Project,
        rootProjectPath: File,
        gradleTasks: List<String>,
        taskId: ExternalSystemTaskId,
        isWaitForCompletion: Boolean = false,

        /** If true, the build output window will not automatically be shown on failure. */
        doNotShowBuildOutputOnFailure: Boolean = false,
        listener: ExternalSystemTaskNotificationListener? = null,
      ) : this(
        project = project,
        taskId = taskId,
        data = RequestData(mode, rootProjectPath, gradleTasks),
        isWaitForCompletion = isWaitForCompletion,
        doNotShowBuildOutputOnFailure = doNotShowBuildOutputOnFailure,
        listener = listener,
      )

      constructor(
        mode: BuildMode?,
        project: Project,
        rootProjectPath: File,
        gradleTasks: List<String>,
        taskId: ExternalSystemTaskId,
        jvmArguments: List<String> = emptyList(),
        commandLineArguments: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        isPassParentEnvs: Boolean = true,
        isWaitForCompletion: Boolean = false,

        /** If true, the build output window will not automatically be shown on failure. */
        doNotShowBuildOutputOnFailure: Boolean = false,
        listener: ExternalSystemTaskNotificationListener? = null,
      ) : this(
        project = project,
        taskId = taskId,
        data = RequestData(mode, rootProjectPath, gradleTasks, jvmArguments, commandLineArguments, env, isPassParentEnvs),
        isWaitForCompletion = isWaitForCompletion,
        doNotShowBuildOutputOnFailure = doNotShowBuildOutputOnFailure,
        listener = listener,
      )
    }

    companion object {
      @JvmStatic
      fun builder(project: Project, rootProjectPath: File, vararg gradleTasks: String): Builder =
        Builder(project, rootProjectPath, gradleTasks.toList())

      @JvmStatic
      fun builder(
        project: Project,
        rootProjectPath: File,
        gradleTasks: Collection<String>,
        executionEnvironment: ExecutionEnvironment? = null,
      ): Builder = Builder(project, rootProjectPath, gradleTasks.toList(), executionEnvironment)

      @JvmStatic
      fun fromExecutionSettings(
        project: Project,
        taskId: ExternalSystemTaskId,
        mode: BuildMode?,
        rootProjectPath: File,
        executionSettings: GradleExecutionSettings,
        isWaitForCompletion: Boolean = false,
        listener: ExternalSystemTaskNotificationListener? = null,
        executionEnvironment: ExecutionEnvironment? = null,
      ): Request =
        ExecutionSettingsBasedRequestImpl(
          project,
          taskId,
          mode,
          rootProjectPath,
          executionSettings,
          isWaitForCompletion,
          listener,
          executionEnvironment,
        )

      @JvmStatic fun copyRequest(request: Request): Request = request.copyRequest()
    }

    data class RequestData(
      val mode: BuildMode?,
      val rootProjectPath: File,
      val gradleTasks: List<String>,
      val jvmArguments: List<String> = emptyList(),
      val commandLineArguments: List<String> = emptyList(),
      val env: Map<String, String> = emptyMap(),
      val isPassParentEnvs: Boolean = true,
    )

    class Builder constructor(project: Project, requestData: RequestData, executionEnvironment: ExecutionEnvironment?) {
      private var request: RequestImpl =
        RequestImpl(
          project = project,
          data = requestData,
          taskId = ExternalSystemTaskId.create(GradleProjectSystemUtil.GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
          executionEnvironment = executionEnvironment,
        )

      constructor(
        project: Project,
        rootProjectPath: File,
        gradleTasks: List<String>,
        executionEnvironment: ExecutionEnvironment? = null,
      ) : this(project, RequestData(null, rootProjectPath, gradleTasks), executionEnvironment)

      fun updateData(update: (RequestData) -> RequestData): Builder {
        request = request.copy(data = update(request.data))
        return this
      }

      fun setMode(value: BuildMode?): Builder = updateData { it.copy(mode = value) }

      fun setTaskId(value: ExternalSystemTaskId): Builder {
        request = request.copy(taskId = value)
        return this
      }

      fun setJvmArguments(value: List<String>): Builder = updateData { it.copy(jvmArguments = value.toList()) }

      fun setCommandLineArguments(value: List<String>): Builder = updateData { it.copy(commandLineArguments = value.toList()) }

      fun withEnvironmentVariables(value: Map<String, String>): Builder = updateData { it.copy(env = request.env + value) }

      fun passParentEnvs(value: Boolean): Builder = updateData { it.copy(isPassParentEnvs = value) }

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
