/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates.recipe

import java.io.File
import java.io.IOException

/**
 * [RecipeExecutor] that collects references as a result of executing instructions in a [Recipe].
 */
internal class FindReferencesRecipeExecutor(private val myContext: RenderingContext) : RecipeExecutor {
  override fun copy(from: File, to: File) {
    if (from.isDirectory) {
      throw RuntimeException("Directories not supported for Find References")
    }
    addSourceFile(from)
    addTargetFile(to)
  }

  override fun instantiate(from: File, to: File) {
    addSourceFile(from)
    addTargetFile(to)
  }

  override fun merge(from: File, to: File) {
    addSourceFile(from)
    addTargetFile(to)
  }

  override fun addGlobalVariable(id: String, value: Any) {
    myContext.paramMap[id] = value
  }

  override fun mkDir(at: File) {}

  override fun addFilesToOpen(file: File) {
    myContext.filesToOpen.add(resolveTargetFile(file))
  }

  override fun applyPlugin(plugin: String) {
    myContext.plugins.add(plugin)
  }

  override fun addSourceSet(type: String, name: String, dir: String) {}

  override fun addClasspath(mavenUrl: String) {
    myContext.classpathEntries.add(mavenUrl)
  }

  override fun addDependency(configuration: String, mavenUrl: String) {
    myContext.dependencies.put(configuration, mavenUrl)
  }

  override fun updateAndSync() {}

  override fun pushFolder(folder: String) {}

  override fun popFolder() {}

  fun addSourceFile(file: File) {
    myContext.sourceFiles.add(resolveSourceFile(file))
  }

  fun addTargetFile(file: File) {
    myContext.targetFiles.add(resolveTargetFile(file))
  }

  private fun resolveSourceFile(file: File): File {
    if (file.isAbsolute) {
      return file
    }
    try {
      return myContext.loader.getSourceFile(file)
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  private fun resolveTargetFile(file: File): File = if (file.isAbsolute) file else File(myContext.outputRoot, file.path)
}
