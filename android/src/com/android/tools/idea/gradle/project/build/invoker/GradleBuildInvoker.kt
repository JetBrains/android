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

import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleUtil
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.gradle.tooling.BuildAction
import org.jetbrains.annotations.TestOnly
import java.io.File

interface GradleBuildInvoker {
  fun cleanProject()

  fun generateSources(modules: Array<Module>)
  fun compileJava(modules: Array<Module>, testCompileType: TestCompileType)
  fun assemble(modules: Array<Module>, testCompileType: TestCompileType)
  fun bundle(modules: Array<Module>)

  fun rebuild()
  fun rebuildWithTempOptions(rootProjectPath: File, options: List<String>)

  /**
   * Executes Gradle tasks requested for each root in separate Gradle invocations. The results (including failed sub-builds) are reported as
   * GradleInvocationResult, however, any unexpected failures are returned as a failed future.
   */
  fun executeTasks(request: List<Request>): ListenableFuture<GradleMultiInvocationResult>
  fun executeTasks(request: Request): ListenableFuture<GradleInvocationResult>

  fun stopBuild(id: ExternalSystemTaskId): Boolean
  fun add(task: AfterGradleInvocationTask)
  fun remove(task: AfterGradleInvocationTask)
  val project: Project

  interface AfterGradleInvocationTask {
    fun execute(result: GradleInvocationResult)
  }

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
    val buildAction: BuildAction<*>? = null,
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
        gradleTasks: List<String>
      ): Builder = Builder(project, rootProjectPath, gradleTasks.toList())
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

      fun setBuildAction(value: BuildAction<*>?): Builder {
        request = request.copy(buildAction = value)
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
      return ServiceManager.getService(project, GradleBuildInvoker::class.java)
    }
  }

  @Deprecated("This property does not return anything useful as its state can change at any moment. It should not be used.")
  val internalIsBuildRunning: Boolean
}
