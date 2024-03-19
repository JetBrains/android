/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.GradleMultiInvocationResult
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import java.io.File

/**
 * Returns a live list of [GradleBuildInvoker.Request] that captures any Gradle execution requests made through
 * [GradleBuildInvoker.executeTasks].
 */
fun Project.hookExecuteTasks(): List<GradleBuildInvoker.Request> {
  val buildInvoker = GradleBuildInvoker.getInstance(this)
  val capturedRequests = mutableListOf<GradleBuildInvoker.Request>()
  val requestCapturingInvoker = object : GradleBuildInvoker {
    override val project: Project get() = buildInvoker.project
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated")
    override val internalIsBuildRunning: Boolean get() = buildInvoker.internalIsBuildRunning

    override fun executeTasks(request: GradleBuildInvoker.Request): ListenableFuture<GradleInvocationResult> {
      capturedRequests.add(request)
      return buildInvoker.executeTasks(request)
    }

    override fun executeTasks(request: List<GradleBuildInvoker.Request>): ListenableFuture<GradleMultiInvocationResult> {
      capturedRequests.addAll(request)
      return buildInvoker.executeTasks(request)
    }

    override fun cleanProject() = notHooked()
    override fun generateSources(modules: Array<Module>) = notHooked()
    override fun compileJava(modules: Array<Module>) = notHooked()
    override fun assemble() = notHooked()
    override fun assemble(modules: Array<Module>) = notHooked()
    override fun bundle(modules: Array<Module>) = notHooked()
    override fun rebuild() = notHooked()
    override fun rebuildWithTempOptions(rootProjectPath: File, options: List<String>) = notHooked()
    override fun generateBaselineProfileSources(
      taskId: ExternalSystemTaskId,
      modules: Array<Module>,
      envVariables: Map<String, String>,
      args: List<String>,
      generateAllVariants: Boolean
    ): ListenableFuture<GradleMultiInvocationResult> = notHooked()
    override fun executeAssembleTasks(assembledModules: Array<Module>, request: List<GradleBuildInvoker.Request>) = notHooked()
    override fun stopBuild(id: ExternalSystemTaskId): Boolean = notHooked()

    private fun notHooked(): Nothing = error("This method is not supported by 'Project.hookExecuteTasks'")
  }
  replaceService(GradleBuildInvoker::class.java, requestCapturingInvoker, this)
  return capturedRequests
}