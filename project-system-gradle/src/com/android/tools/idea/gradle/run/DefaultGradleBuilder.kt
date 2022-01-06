/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.run

import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.util.BuildMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.nio.file.Path

internal class DefaultGradleBuilder(
  private val project: Project,
  private val assembledModules: Array<Module>,
  private val tasks: Map<Path, Collection<String>>,
  private val buildMode: BuildMode?
) : BeforeRunBuilder {
  override fun build(commandLineArguments: List<String>): AssembleInvocationResult? {
    if (tasks.values.flatten().isEmpty()) {
      Logger.getInstance(DefaultGradleBuilder::class.java).error("Unable to determine gradle tasks to execute")
      return null
    }
    return GradleTaskRunner.run(project, assembledModules, tasks, buildMode, commandLineArguments)
  }
}