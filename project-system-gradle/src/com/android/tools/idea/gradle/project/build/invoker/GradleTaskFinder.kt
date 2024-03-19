/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import java.nio.file.Path

class GradleTaskFinder {
  companion object {
    @JvmStatic
    fun getInstance(): GradleTaskFinder {
      return ApplicationManager.getApplication().getService(GradleTaskFinder::class.java)
    }
  }

  @JvmOverloads
  fun findTasksToExecute(modules: Array<Module>, buildMode: BuildMode, expandModules: Boolean = false): ListMultimap<Path, String> {
    val result = findTasksToExecuteCore(modules, buildMode, expandModules)
    if (result.isEmpty) {
      GradleTaskFinderNotifier.notifyNoTaskFound(modules, buildMode)
    }
    return result
  }

  private fun findTasksToExecuteCore(
    modules: Array<Module>,
    buildMode: BuildMode,
    expandModules: Boolean = false
  ): ArrayListMultimap<Path, String> {
    val project = modules.firstOrNull()?.project ?: return ArrayListMultimap.create()
    val worker = GradleTaskFinderWorker(project, buildMode, modules.asList(), expandModules)

    val resultAsMap = worker.find()

    val result = ArrayListMultimap.create<Path, String>()
    resultAsMap.forEach { (k, v) -> result.putAll(k, v) }
    return result
  }
}
